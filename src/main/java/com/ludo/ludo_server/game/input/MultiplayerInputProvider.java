package com.ludo.ludo_server.game.input;

import com.ludo.ludo_server.game.connection.GameRoom;
import com.ludo.ludo_server.game.connection.StompGameEventBroadcaster;
import com.ludo.ludo_server.game.state.GameState;
import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import com.ludo.ludo_server.player.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.ludo.ludo_server.game.websocket.controller.ResponseType.*;

/**
 * Temporary InputProvider for multiplayer games
 * This bridges the Game class with the WebSocket system
 */
public class MultiplayerInputProvider implements InputProvider {

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
            System.err.println("No current player set for choice!");
            return min;
        }

        String sessionId = gameRoom.getSessionIdForPlayer(player);
        if (sessionId == null) {
            System.err.println("Could not find session for player: " + player.getPlayerName());
            return min;
        }

        while (true) {
            try {
                CompletableFuture<Integer> future = new CompletableFuture<>();
                pendingChoices.put(sessionId, future);

                // DON'T send another message - the move options were already sent
                // Just wait for the response
                System.out.println("Waiting for choice from " + player.getPlayerName() + " (move options already sent)");

                Integer choice = future.get();

                if (choice >= min && choice <= max) {
                    System.out.println("Valid choice " + choice + " from " + player.getPlayerName());
                    return choice;
                } else {
                    System.out.println("Invalid choice " + choice + " from " + player.getPlayerName() + ". Expected: " + min + "-" + max);
                    broadcaster.sendToPlayer(sessionId,
                            GameResponse.error(INVALID_CHOICE,
                                    "Invalid choice " + choice + "! Please enter a number between " + min + " and " + max));
                }

            } catch (Exception e) {
                System.err.println("Error getting choice from player: " + e.getMessage());
                return min; // Fallback
            }
        }
    }


    @Override
    public void waitForInput(String prompt) {
        Player player = currentPlayer.get();
        if (player == null) {
            System.err.println("No current player set for input!");
            return;
        }

        String sessionId = gameRoom.getSessionIdForPlayer(player);
        if (sessionId == null) {
            System.err.println("Could not find session for player: " + player.getPlayerName());
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

            System.out.println("Sent input request to " + player.getPlayerName() + " (" + sessionId + "): " + prompt);

            // Wait for any response
            String input = future.get();
            System.out.println("Received input '" + input + "' from " + player.getPlayerName());

        } catch (Exception e) {
            System.err.println("Error waiting for input: " + e.getMessage());
        }
    }

    @Override
    public void sendMessage(String message) {
        // Broadcast game messages to all players in the game
        broadcaster.broadcastToGame(gameId,
                GameResponse.success(GAME_MESSAGE, message, null));

        System.out.println("GAME MESSAGE [" + gameId + "]: " + message);
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
            System.out.println("Processing choice " + choice + " from session " + sessionId);
            future.complete(choice); // This resumes the game thread!
        } else {
            System.err.println("No pending choice for session: " + sessionId);
        }
    }

    public void sendMoveOptions(String moveMessage, int max) {
        Player player = currentPlayer.get();
        if (player == null) {
            System.err.println("No current player set for move options!");
            return;
        }

        String sessionId = gameRoom.getSessionIdForPlayer(player);
        if (sessionId == null) {
            System.err.println("Could not find session for player: " + player.getPlayerName());
            return;
        }

        // Add the choice prompt to the move message
        String fullMessage = moveMessage + "\nEnter game option (1-" + max + ")";

        // Send complete move list + prompt to current player's personal queue only
        broadcaster.sendToPlayer(sessionId,
                GameResponse.success(MOVE_OPTIONS, fullMessage, null));

        System.out.println("Move options sent to " + player.getPlayerName() + " (" + sessionId + ")");
    }

    public void handlePlayerInput(String sessionId, String input) {
        CompletableFuture<String> future = pendingInputs.remove(sessionId);
        if (future != null) {
            System.out.println("Processing input '" + input + "' from session " + sessionId);
            future.complete(input); // This resumes the game thread!
        } else {
            System.err.println("No pending input for session: " + sessionId);
        }
    }

    // ========== DEBUG METHODS ==========

    public void debugPendingRequests() {
        System.out.println("Pending choices for sessions: " + pendingChoices.keySet());
        System.out.println("Pending inputs for sessions: " + pendingInputs.keySet());
    }

    public boolean hasPendingRequest(String sessionId) {
        return pendingChoices.containsKey(sessionId) || pendingInputs.containsKey(sessionId);
    }

    public void sendTurnNotification(String message) {
        Player player = currentPlayer.get();
        if (player == null) {
            System.err.println("No current player set for turn notification!");
            return;
        }

        String sessionId = gameRoom.getSessionIdForPlayer(player);
        if (sessionId == null) {
            System.err.println("Could not find session for player: " + player.getPlayerName());
            return;
        }

        // Send ONLY to current player's personal queue
        broadcaster.sendToPlayer(sessionId,
                GameResponse.success(YOUR_TURN, message, null));

        System.out.println("📤 Turn notification sent to " + player.getPlayerName() + " (" + sessionId + "): " + message);
    }

    // Clear any stuck requests (for cleanup)
    public void clearPendingRequests() {
        System.out.println("Clearing " + pendingChoices.size() + " pending choices and " + pendingInputs.size() + " pending inputs");

        // Complete any pending futures with default values to avoid hanging
        pendingChoices.values().forEach(future -> future.complete(1));
        pendingInputs.values().forEach(future -> future.complete(""));

        pendingChoices.clear();
        pendingInputs.clear();
    }
}