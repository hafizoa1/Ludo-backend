package com.ludo.ludo_server.game.input;

import com.ludo.ludo_server.game.connection.GameRoom;
import com.ludo.ludo_server.game.connection.StompGameEventBroadcaster;
import com.ludo.ludo_server.game.state.GameState;
import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import com.ludo.ludo_server.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.ludo.ludo_server.game.websocket.controller.ResponseType.*;

/**
 * Temporary InputProvider for multiplayer games
 * This bridges the Game class with the WebSocket system
 */
public class MultiplayerInputProvider implements InputProvider {

    private static final Logger logger = LoggerFactory.getLogger(MultiplayerInputProvider.class);

    private static final int CHOICE_TIMEOUT_SECONDS = 30;

    private String gameId;
    private StompGameEventBroadcaster broadcaster;
    private GameRoom gameRoom;

    // Store pending requests per session ID
    private Map<String, CompletableFuture<Integer>> pendingChoices = new ConcurrentHashMap<>();
    private Map<String, CompletableFuture<String>> pendingInputs = new ConcurrentHashMap<>();

    // Track current player context - set by Game class
    private ThreadLocal<Player> currentPlayer = new ThreadLocal<>();

    public MultiplayerInputProvider(String gameId, StompGameEventBroadcaster broadcaster, GameRoom gameRoom) {
        this.gameId = gameId;
        this.broadcaster = broadcaster;
        this.gameRoom = gameRoom;
    }

    // Method for Game class to set current player context
    public void setCurrentPlayer(Player player) {
        currentPlayer.set(player);
    }

    @Override
    public int getChoice(int min, int max, String prompt) {
        Player player = currentPlayer.get();
        if (player == null) {
            logger.warn("No current player set for choice!");
            return min;
        }

        String sessionId = gameRoom.getSessionIdForPlayer(player);
        if (sessionId == null) {
            logger.warn("Could not find session for player: {}", player.getPlayerName());
            return min;
        }

        while (true) {
            try {
                CompletableFuture<Integer> future = new CompletableFuture<>();
                pendingChoices.put(sessionId, future);

                logger.debug("Waiting for choice from {} (timeout: {}s)", player.getPlayerName(), CHOICE_TIMEOUT_SECONDS);

                // Add timeout to prevent indefinite blocking
                Integer choice = future.get(CHOICE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (choice >= min && choice <= max) {
                    logger.debug("Valid choice {} from {}", choice, player.getPlayerName());
                    return choice;
                } else {
                    logger.warn("Invalid choice {} from {}. Expected: {}-{}", choice, player.getPlayerName(), min, max);
                    broadcaster.sendToPlayer(sessionId,
                            GameResponse.error(INVALID_CHOICE,
                                    "Invalid choice " + choice + "! Please enter a number between " +
                                    min + " and " + max));
                }

            } catch (TimeoutException e) {
                // Player didn't respond within timeout - throw exception to end game
                pendingChoices.remove(sessionId);
                logger.warn("TIMEOUT: Player {} did not respond within {} seconds", player.getPlayerName(), CHOICE_TIMEOUT_SECONDS);

                throw new PlayerTimeoutException(sessionId, player.getPlayerId(), player.getPlayerName());

            } catch (InterruptedException e) {
                logger.warn("Thread interrupted while waiting for choice");
                Thread.currentThread().interrupt();
                throw new PlayerTimeoutException(sessionId, player.getPlayerId(), player.getPlayerName());
            } catch (ExecutionException e) {
                logger.error("Error getting choice from player: {}", e.getMessage(), e);
                throw new PlayerTimeoutException(sessionId, player.getPlayerId(), player.getPlayerName());
            }
        }
    }


    @Override
    public void waitForInput(String prompt) {
        Player player = currentPlayer.get();
        if (player == null) {
            logger.warn("No current player set for input!");
            return;
        }

        String sessionId = gameRoom.getSessionIdForPlayer(player);
        if (sessionId == null) {
            logger.warn("Could not find session for player: {}", player.getPlayerName());
            return;
        }

        try {
            // Create future for any input (like "press anything to continue")
            CompletableFuture<String> future = new CompletableFuture<>();
            pendingInputs.put(sessionId, future);

            // Get current game state to send with the input request
            GameState currentGameState = gameRoom.getGame().getGameState();

            GameResponse inputRequest = GameResponse.success(INPUT_REQUIRED, prompt, currentGameState);
            broadcaster.sendToSession(sessionId, inputRequest);

            logger.debug("Sent input request to {} (timeout: {}s)", player.getPlayerName(), CHOICE_TIMEOUT_SECONDS);

            // Wait for any response with timeout
            String input = future.get(CHOICE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            logger.debug("Received input '{}' from {}", input, player.getPlayerName());

        } catch (TimeoutException e) {
            // Player didn't respond within timeout
            pendingInputs.remove(sessionId);
            logger.warn("TIMEOUT: Player {} did not respond to input request within {} seconds",
                    player.getPlayerName(), CHOICE_TIMEOUT_SECONDS);

            throw new PlayerTimeoutException(sessionId, player.getPlayerId(), player.getPlayerName());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Thread interrupted while waiting for input");
            throw new PlayerTimeoutException(sessionId, player.getPlayerId(), player.getPlayerName());
        } catch (ExecutionException e) {
            logger.error("Error waiting for input: {}", e.getMessage(), e);
            throw new PlayerTimeoutException(sessionId, player.getPlayerId(), player.getPlayerName());
        }
    }

    @Override
    public void sendMessage(String message) {
        // Broadcast game messages to all players in the game
        broadcaster.broadcastToGame(gameId,
                GameResponse.success(GAME_MESSAGE, message, null));

        logger.debug("GAME MESSAGE [{}]: {}", gameId, message);
    }

    @Override
    public String getName(String prompt) {
        // Players already have names from game setup
        Player player = currentPlayer.get();
        return player != null ? player.getPlayerName() : "Player";
    }

    // ========== RESPONSE HANDLERS ==========
    // These are called by GameMessageController when players respond

    public void handlePlayerChoice(String sessionId, int choice) {
        CompletableFuture<Integer> future = pendingChoices.remove(sessionId);
        if (future != null) {
            logger.debug("Processing choice {} from session {}", choice, sessionId);
            future.complete(choice); // This resumes the game thread!
        } else {
            logger.warn("No pending choice for session: {}", sessionId);
        }
    }

    public void sendMoveOptions(String moveMessage, int max) {
        Player player = currentPlayer.get();
        if (player == null) {
            logger.warn("No current player set for move options!");
            return;
        }

        String sessionId = gameRoom.getSessionIdForPlayer(player);
        if (sessionId == null) {
            logger.warn("Could not find session for player: {}", player.getPlayerName());
            return;
        }

        // Add the choice prompt to the move message
        String fullMessage = moveMessage + "\nEnter game option (1-" + max + ")";

        // Send complete move list + prompt to current player's personal queue only,
        // with the structured options attached via the current GameState snapshot
        GameState currentGameState = gameRoom.getGame().getGameState();
        broadcaster.sendToPlayer(sessionId,
                GameResponse.success(MOVE_OPTIONS, fullMessage, currentGameState));

        logger.debug("Move options sent to {} ({})", player.getPlayerName(), sessionId);
    }

    public void sendCaptureOptions(String captureMessage, int max) {
        Player player = currentPlayer.get();
        if (player == null) {
            logger.warn("No current player set for capture options!");
            return;
        }

        String sessionId = gameRoom.getSessionIdForPlayer(player);
        if (sessionId == null) {
            logger.warn("Could not find session for player: {}", player.getPlayerName());
            return;
        }

        // Add the choice prompt to the capture message
        String fullMessage = captureMessage + "\nChoose which piece to capture (1-" + max + ")";

        // Send capture options to current player's personal queue, with the
        // structured options attached via the current GameState snapshot
        GameState currentGameState = gameRoom.getGame().getGameState();
        broadcaster.sendToPlayer(sessionId,
                GameResponse.success(CAPTURE_OPTIONS, fullMessage, currentGameState));

        logger.debug("Capture options sent to {} ({})", player.getPlayerName(), sessionId);
    }

    public void handlePlayerInput(String sessionId, String input) {
        CompletableFuture<String> future = pendingInputs.remove(sessionId);
        if (future != null) {
            logger.debug("Processing input '{}' from session {}", input, sessionId);
            future.complete(input); // This resumes the game thread!
        } else {
            logger.warn("No pending input for session: {}", sessionId);
        }
    }

    // ========== DEBUG METHODS ==========

    public void debugPendingRequests() {
        logger.debug("Pending choices for sessions: {}", pendingChoices.keySet());
        logger.debug("Pending inputs for sessions: {}", pendingInputs.keySet());
    }

    public boolean hasPendingRequest(String sessionId) {
        return pendingChoices.containsKey(sessionId) || pendingInputs.containsKey(sessionId);
    }

    public void sendTurnNotification(String message) {
        Player player = currentPlayer.get();
        if (player == null) {
            logger.warn("No current player set for turn notification!");
            return;
        }

        String sessionId = gameRoom.getSessionIdForPlayer(player);
        if (sessionId == null) {
            logger.warn("Could not find session for player: {}", player.getPlayerName());
            return;
        }

        // Send ONLY to current player's personal queue
        broadcaster.sendToPlayer(sessionId,
                GameResponse.success(YOUR_TURN, message, null));

        logger.debug("Turn notification sent to {} ({}): {}", player.getPlayerName(), sessionId, message);
    }

    // Clear any stuck requests (for cleanup)
    public void clearPendingRequests() {
        logger.debug("Clearing {} pending choices and {} pending inputs", pendingChoices.size(), pendingInputs.size());

        // Complete any pending futures with default values to avoid hanging
        pendingChoices.values().forEach(future -> future.complete(1));
        pendingInputs.values().forEach(future -> future.complete(""));

        pendingChoices.clear();
        pendingInputs.clear();
    }
}