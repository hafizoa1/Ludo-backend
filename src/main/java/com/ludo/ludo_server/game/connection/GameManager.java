package com.ludo.ludo_server.game.connection;


/**
 * GameManager - Central orchestrator for multiplayer game management
 *
 * Purpose: Handles all aspects of multiplayer game lifecycle and player management.
 * Acts as the main entry point for game-related operations from the WebSocket layer.
 *
 * Key Responsibilities:
 * - Create new multiplayer game rooms
 * - Handle players joining/leaving games
 * - Manage multiple concurrent games
 * - Assign player colors and IDs automatically
 * - Start games when enough players have joined
 * - Validate game actions and player permissions
 *
 * Data Management:
 * - Maintains map of all active GameRoom instances
 * - Uses SessionMapper to link WebSocket sessions to games
 * - Generates unique game IDs for room identification
 *
 * Used by: GameMessageController for all multiplayer game operations
 *
 * Why needed: Centralizes complex multiplayer logic, making the WebSocket
 * message controller thin and focused only on message routing.
 */

import com.ludo.ludo_server.game.Game;
import com.ludo.ludo_server.game.input.MultiplayerInputProvider;
import com.ludo.ludo_server.game.input.PlayerTimeoutException;
import com.ludo.ludo_server.game.state.GameState;
import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import com.ludo.ludo_server.player.HumanPlayer;
import com.ludo.ludo_server.player.Player;
import com.ludo.ludo_server.player.PlayerColor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.ludo.ludo_server.game.websocket.controller.ResponseType.*;


@Component
public class GameManager {

    @Autowired
    private GameIdGenerator gameIdGenerator;

    @Autowired
    private SessionMapper sessionMapper;

    @Autowired
    private StompGameEventBroadcaster broadcaster;

    private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private static final int DEFAULT_MAX_PLAYERS = 2;

    // Scheduler for disconnect timeouts
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Track scheduled disconnect tasks so we can cancel them on reconnect
    private final Map<String, ScheduledFuture<?>> disconnectTasks = new ConcurrentHashMap<>();

    // 2-player setup: each player gets 2 colors
    private static final List<PlayerColor> PLAYER1_COLORS = List.of(PlayerColor.RED, PlayerColor.YELLOW);
    private static final List<PlayerColor> PLAYER2_COLORS = List.of(PlayerColor.BLUE, PlayerColor.GREEN);

    // =============================================================================
    // GAME ROOM MANAGEMENT
    // =============================================================================

    public GameResponse createGame(String sessionId, String playerId) {
        String gameId = gameIdGenerator.generateGameId();
        GameRoom gameRoom = new GameRoom(gameId, DEFAULT_MAX_PLAYERS, broadcaster);

        Player creator = new HumanPlayer("player1", "Player 1", PLAYER1_COLORS);
        gameRoom.addPlayer(sessionId, creator);

        gameRooms.put(gameId, gameRoom);

        // Register PlayerSession for reconnection support
        sessionMapper.createPlayerSession(playerId, sessionId, gameId, creator);

        return GameResponse.success(GAME_CREATED,
                "Game " + gameId + " created. Waiting for players (1/" + DEFAULT_MAX_PLAYERS + ")");
    }

    public GameResponse joinGame(String sessionId, String gameId, String playerId) {
        GameRoom gameRoom = gameRooms.get(gameId);

        if (gameRoom == null) {
            return GameResponse.error(GAME_NOT_FOUND, "Game " + gameId + " not found");
        }

        // Check if this player is reconnecting
        PlayerSession existingSession = sessionMapper.findPlayerSessionByPlayerId(playerId);

        if (existingSession != null && gameId.equals(existingSession.getGameId())) {
            // RECONNECTION: Same player rejoining same game
            System.out.println("🔄 Player " + playerId + " reconnecting to game " + gameId);

            // Update the session with new WebSocket sessionId
            sessionMapper.updatePlayerSession(playerId, sessionId);

            // Cancel any pending disconnect timeout
            ScheduledFuture<?> disconnectTask = disconnectTasks.remove(playerId);
            if (disconnectTask != null) {
                disconnectTask.cancel(false);
                System.out.println("✅ Cancelled disconnect timer for player: " + playerId);
            }

            // Update GameRoom mapping with new sessionId
            gameRoom.reconnectPlayer(existingSession.getCurrentSessionId(), sessionId);

            // Notify all players about reconnection
            String reconnectMessage = existingSession.getDisplayName() + " reconnected!";
            broadcaster.broadcastToGame(gameId,
                GameResponse.success(GAME_MESSAGE, reconnectMessage, null));

            return GameResponse.success(JOINED_GAME, "Reconnected to game " + gameId);
        }

        // NEW PLAYER joining
        if (!gameRoom.canJoin()) {
            return GameResponse.error(GAME_FULL, "Game " + gameId + " is full or already started");
        }

        List<PlayerColor> playerColors = gameRoom.getSessionIds().size() == 0 ? PLAYER1_COLORS : PLAYER2_COLORS;
        int playerNumber = gameRoom.getSessionIds().size() + 1;
        Player player = new HumanPlayer("player" + playerNumber, "Player " + playerNumber, playerColors);

        gameRoom.addPlayer(sessionId, player);

        // Register PlayerSession for reconnection support
        sessionMapper.createPlayerSession(playerId, sessionId, gameId, player);

        int currentPlayers = gameRoom.getSessionIds().size();
        String message = "Joined game " + gameId + ". Waiting for players (" + currentPlayers + "/" + DEFAULT_MAX_PLAYERS + ")";

        System.out.println("🎮 After join: " + currentPlayers + " players, isReady: " + gameRoom.isReady());

        if (gameRoom.isReady()) {
            System.out.println("🚀 Starting game " + gameId);
            gameRoom.startGame();
            message = "All players joined! Game starting...";
        }

        return GameResponse.success(JOINED_GAME, message);
    }

    public GameResponse leaveGame(String sessionId) {
        String gameId = sessionMapper.getGameId(sessionId);

        if (gameId == null) {
            return GameResponse.error(NO_GAME, "You are not in any game");
        }

        GameRoom gameRoom = gameRooms.get(gameId);
        if (gameRoom != null) {
            gameRoom.removePlayer(sessionId);

            if (gameRoom.getSessionIds().isEmpty()) {
                gameRooms.remove(gameId);
            }
        }

        sessionMapper.removeSession(sessionId);
        return GameResponse.success(LEFT_GAME, "Left game " + gameId);
    }

    // =============================================================================
    // GAME ACTIONS
    // =============================================================================

    public GameResponse handleDiceRoll(String sessionId) {
        GameRoom gameRoom = getGameRoomBySession(sessionId);

        if (gameRoom == null || gameRoom.getGame() == null) {
            return GameResponse.error(NO_GAME, "No active game found");
        }

        Game game = gameRoom.getGame();
        Player currentPlayer = game.getCurrentPlayer();
        Player requestingPlayer = gameRoom.getPlayer(sessionId);

        if (currentPlayer == null) {
            return GameResponse.error(GAME_NOT_READY, "Game not ready for dice roll");
        }

        if (!currentPlayer.equals(requestingPlayer)) {
            return GameResponse.error(NOT_YOUR_TURN, "It's " + currentPlayer.getPlayerName() + "'s turn");
        }

        if (game.getInputProvider() instanceof MultiplayerInputProvider) {
            MultiplayerInputProvider inputProvider = (MultiplayerInputProvider) game.getInputProvider();
            if (inputProvider.hasPendingRequest(sessionId)) {
                return GameResponse.error(PENDING_CHOICE,
                        "You must make a choice before rolling again! Available choices shown above.");
            }
        }

        try {
            game.getDice().roll();
            String die1 = String.valueOf(game.getDice().getDie1());
            String die2 = String.valueOf(game.getDice().getDie2());

            broadcaster.broadcastToGame(gameRoom.getGameId(),
                    GameResponse.success(DICE_ROLLED,
                            (die1 + die2), null));

            String gameId = gameRoom.getGameId();

            CompletableFuture.runAsync(() -> {
                try {
                    game.continueAfterDiceRoll();
                } catch (PlayerTimeoutException e) {
                    // Player timed out - end the game
                    System.err.println("Player timeout detected: " + e.getPlayerName());
                    handlePlayerTimeout(gameId, e);
                } catch (Exception e) {
                    System.err.println("Error continuing game after dice roll: " + e.getMessage());
                    e.printStackTrace();
                }
            });

            return GameResponse.success(DICE_ROLL_RECEIVED, "Processing dice roll...");

        } catch (Exception e) {
            return GameResponse.error(DICE_ROLL_ERROR, "Error rolling dice: " + e.getMessage());
        }
    }

    public GameResponse handlePlayerChoice(String sessionId, int choice) {
        try {
            GameRoom gameRoom = getGameRoomBySession(sessionId);
            if (gameRoom == null || gameRoom.getGame() == null) {
                return GameResponse.error(NO_GAME, "No active game found");
            }

            Game game = gameRoom.getGame();
            if (game.getInputProvider() instanceof MultiplayerInputProvider) {
                MultiplayerInputProvider inputProvider = (MultiplayerInputProvider) game.getInputProvider();

                if (!inputProvider.hasPendingRequest(sessionId)) {
                    return GameResponse.error(NO_PENDING_CHOICE, "No choice currently required from you");
                }

                inputProvider.handlePlayerChoice(sessionId, choice);

                //GameState updatedGameState = game.getGameState();

                //broadcaster.broadcastToGame(gameRoom.getGameId(),
                       // GameResponse.success("GAME_STATE_UPDATE", "Move executed", updatedGameState));

                return GameResponse.success(CHOICE_RECEIVED, "Choice processed", null);
            }

            return GameResponse.error(INVALID_GAME, "Not a multiplayer game");

        } catch (Exception e) {
            System.err.println("Error in handlePlayerChoice: " + e.getMessage());
            return GameResponse.error(INVALID_CHOICE, "Error processing choice: " + e.getMessage());
        }
    }

    public GameResponse getGameState(String sessionId) {
        GameRoom gameRoom = getGameRoomBySession(sessionId);

        if (gameRoom == null || gameRoom.getGame() == null) {
            return GameResponse.error(NO_GAME, "No active game found");
        }

        GameState gameState = gameRoom.getGame().getGameState();
        return GameResponse.success(GAME_STATE, "Current game state", gameState);
    }

    // =============================================================================
    // DISCONNECT & RECONNECTION HANDLING
    // =============================================================================

    /**
     * Mark player as intentionally leaving (not disconnected)
     */
    public void markPlayerLeft(String sessionId) {
        sessionMapper.markPlayerLeft(sessionId);

        // Cancel any pending disconnect timer
        PlayerSession playerSession = sessionMapper.findPlayerSessionBySessionId(sessionId);
        if (playerSession != null) {
            ScheduledFuture<?> disconnectTask = disconnectTasks.remove(playerSession.getPlayerId());
            if (disconnectTask != null) {
                disconnectTask.cancel(false);
                System.out.println("✅ Cancelled disconnect timer for leaving player: " + playerSession.getPlayerId());
            }
        }
    }

    /**
     * Handle player disconnect - called by WebSocketEventListener
     */
    public void handlePlayerDisconnect(String sessionId) {
        System.out.println("🔍 [DISCONNECT DEBUG] handlePlayerDisconnect called with sessionId: " + sessionId);

        PlayerSession playerSession = sessionMapper.findPlayerSessionBySessionId(sessionId);

        if (playerSession == null) {
            System.out.println("⚠️ [DISCONNECT DEBUG] No player session found for sessionId: " + sessionId);
            return;
        }

        String gameId = playerSession.getGameId();
        String playerId = playerSession.getPlayerId();
        String playerName = playerSession.getDisplayName();

        System.out.println("🔌 [DISCONNECT DEBUG] Handling disconnect for:");
        System.out.println("   Player ID: " + playerId);
        System.out.println("   Player Name: " + playerName);
        System.out.println("   Game ID: " + gameId);

        // Get the game room
        GameRoom gameRoom = getGameRoom(gameId);
        if (gameRoom == null) {
            System.out.println("⚠️ [DISCONNECT DEBUG] Game room not found: " + gameId);
            return;
        }

        // Notify opponent that player disconnected
        String message = playerName + " disconnected. Waiting 30 seconds for reconnection...";
        System.out.println("📢 [DISCONNECT DEBUG] Broadcasting disconnect message to game: " + message);
        broadcaster.broadcastToGame(gameId,
            GameResponse.success(GAME_MESSAGE, message, null));

        // Start 30-second timer
        System.out.println("⏰ [DISCONNECT DEBUG] Scheduling 30-second disconnect timeout timer...");
        System.out.println("   [DISCONNECT DEBUG] Current time: " + System.currentTimeMillis());
        System.out.println("   [DISCONNECT DEBUG] Timeout will fire at: " + (System.currentTimeMillis() + 30000));

        ScheduledFuture<?> disconnectTask = scheduler.schedule(() -> {
            System.out.println("⏰ [DISCONNECT DEBUG] 30-second timeout fired! Calling handleDisconnectTimeout()");
            handleDisconnectTimeout(playerId, gameId);
        }, 30, TimeUnit.SECONDS);

        // Store the task so we can cancel it if player reconnects
        disconnectTasks.put(playerId, disconnectTask);

        System.out.println("✅ [DISCONNECT DEBUG] Disconnect timer scheduled and stored for player: " + playerId);
    }

    /**
     * Called after 30 seconds if player hasn't reconnected
     */
    private void handleDisconnectTimeout(String playerId, String gameId) {
        System.out.println("=====================================");
        System.out.println("⏰ [TIMEOUT DEBUG] handleDisconnectTimeout() CALLED");
        System.out.println("   [TIMEOUT DEBUG] Player ID: " + playerId);
        System.out.println("   [TIMEOUT DEBUG] Game ID: " + gameId);
        System.out.println("   [TIMEOUT DEBUG] Current time: " + System.currentTimeMillis());
        System.out.println("⏰ Disconnect timeout reached for player: " + playerId);

        // Check if player is still disconnected
        PlayerSession playerSession = sessionMapper.findPlayerSessionByPlayerId(playerId);

        if (playerSession == null || playerSession.getStatus() == PlayerStatus.CONNECTED) {
            // Player reconnected or session was cleaned up - do nothing
            System.out.println("✅ Player " + playerId + " reconnected before timeout");
            return;
        }

        System.out.println("❌ Player " + playerId + " did not reconnect - ending game");

        // Player still disconnected - end game and declare opponent winner
        GameRoom gameRoom = getGameRoom(gameId);
        if (gameRoom != null) {
            // Find the opponent (the player who is still connected)
            String disconnectedSessionId = playerSession.getCurrentSessionId();
            String winnerSessionId = null;
            String winnerName = "Opponent";

            for (String sid : gameRoom.getSessionIds()) {
                if (!sid.equals(disconnectedSessionId)) {
                    winnerSessionId = sid;
                    Player winner = gameRoom.getPlayer(sid);
                    if (winner != null) {
                        winnerName = winner.getPlayerName();
                    }
                    break;
                }
            }

            String disconnectedPlayerName = playerSession.getDisplayName();

            // Send GAME_ENDED_TIMEOUT messages to both players
            if (winnerSessionId != null) {
                System.out.println("📤 [TIMEOUT DEBUG] Sending GAME_ENDED_TIMEOUT to winner: " + winnerSessionId);
                System.out.println("   Winner name: " + winnerName);
                System.out.println("   Disconnected player: " + disconnectedPlayerName);

                // Get winner's colors
                Player winnerPlayer = gameRoom.getPlayer(winnerSessionId);
                String winnerColors = "";
                if (winnerPlayer != null) {
                    List<PlayerColor> colors = winnerPlayer.getColors();
                    winnerColors = colors.get(0).toString().toLowerCase() + "," + colors.get(1).toString().toLowerCase();
                }

                // Get disconnected player's colors
                Player disconnectedPlayer = gameRoom.getPlayer(disconnectedSessionId);
                String disconnectedColors = "";
                if (disconnectedPlayer != null) {
                    List<PlayerColor> colors = disconnectedPlayer.getColors();
                    disconnectedColors = colors.get(0).toString().toLowerCase() + "," + colors.get(1).toString().toLowerCase();
                }

                // Message to winner (format: "color1,color2|message")
                broadcaster.sendToPlayer(winnerSessionId,
                    GameResponse.success(GAME_ENDED_TIMEOUT,
                        winnerColors + "|" + disconnectedPlayerName + " disconnected and did not reconnect. Game has ended - You win!"));

                System.out.println("📤 [TIMEOUT DEBUG] Sending GAME_ENDED_TIMEOUT to disconnected player: " + disconnectedSessionId);

                // Note: disconnected player won't receive this since they're disconnected
                // but send it anyway in case they reconnect at the last second
                broadcaster.sendToPlayer(disconnectedSessionId,
                    GameResponse.error(GAME_ENDED_TIMEOUT,
                        disconnectedColors + "|You disconnected and did not reconnect. Game has ended - " + winnerName + " wins."));
            }

            System.out.println("📤 [TIMEOUT DEBUG] Broadcasting GAME_ENDED_TIMEOUT to game room: " + gameId);

            // Broadcast to game room
            broadcaster.broadcastToGame(gameId,
                GameResponse.success(GAME_ENDED_TIMEOUT,
                    disconnectedPlayerName + " disconnected. Game has ended."));

            // Clean up the game room
            gameRooms.remove(gameId);

            // Clean up all player sessions in this game
            for (String sid : gameRoom.getSessionIds()) {
                PlayerSession session = sessionMapper.findPlayerSessionBySessionId(sid);
                if (session != null) {
                    sessionMapper.removePlayerSession(session.getPlayerId());
                }
            }
        } else {
            // Game room already removed
            sessionMapper.removePlayerSession(playerId);
        }

        // Clean up the disconnect task
        disconnectTasks.remove(playerId);

        System.out.println("🗑️ Cleaned up game and player session for disconnect timeout");
    }

    /**
     * Handle player timeout - end game and declare opponent winner
     */
    private void handlePlayerTimeout(String gameId, PlayerTimeoutException timeoutException) {
        System.err.println("⏰ Handling timeout for player " + timeoutException.getPlayerName() +
                          " in game " + gameId);

        GameRoom gameRoom = getGameRoom(gameId);
        if (gameRoom == null) {
            System.err.println("Game room not found: " + gameId);
            return;
        }

        // Find the opponent (the player who DIDN'T timeout)
        String timedOutSessionId = timeoutException.getSessionId();
        String winnerSessionId = null;
        String winnerName = "Opponent";

        for (String sid : gameRoom.getSessionIds()) {
            if (!sid.equals(timedOutSessionId)) {
                winnerSessionId = sid;
                Player winner = gameRoom.getPlayer(sid);
                if (winner != null) {
                    winnerName = winner.getPlayerName();
                }
                break;
            }
        }

        String timedOutPlayerName = timeoutException.getPlayerName();

        // Broadcast game ended to both players with different messages
        if (winnerSessionId != null) {
            // Message to winner
            broadcaster.sendToPlayer(winnerSessionId,
                GameResponse.success(GAME_ENDED_TIMEOUT,
                    timedOutPlayerName + " is unresponsive. Game has ended - You win!"));

            // Message to player who timed out (if still connected)
            broadcaster.sendToPlayer(timedOutSessionId,
                GameResponse.error(GAME_ENDED_TIMEOUT,
                    "You were unresponsive. Game has ended - " + winnerName + " wins."));
        }

        // Broadcast to game room (general message)
        broadcaster.broadcastToGame(gameId,
            GameResponse.success(GAME_ENDED_TIMEOUT,
                timedOutPlayerName + " is unresponsive. Game has ended."));

        // Clean up the game
        gameRooms.remove(gameId);

        // Clean up player sessions
        for (String sid : gameRoom.getSessionIds()) {
            PlayerSession session = sessionMapper.findPlayerSessionBySessionId(sid);
            if (session != null) {
                sessionMapper.removePlayerSession(session.getPlayerId());
            }
        }

        // Cancel any pending disconnect timers
        for (String sid : gameRoom.getSessionIds()) {
            PlayerSession session = sessionMapper.findPlayerSessionBySessionId(sid);
            if (session != null) {
                ScheduledFuture<?> task = disconnectTasks.remove(session.getPlayerId());
                if (task != null) {
                    task.cancel(false);
                }
            }
        }

        System.out.println("✅ Game " + gameId + " ended due to timeout. Winner: " + winnerName);
    }

    // =============================================================================
    // HELPER METHODS
    // =============================================================================

    public GameRoom getGameRoom(String gameId) {
        return gameRooms.get(gameId);
    }

    public GameRoom getGameRoomBySession(String sessionId) {
        String gameId = sessionMapper.getGameId(sessionId);
        return gameId != null ? gameRooms.get(gameId) : null;
    }

    public Player getPlayerBySession(String sessionId) {
        GameRoom gameRoom = getGameRoomBySession(sessionId);
        return gameRoom != null ? gameRoom.getPlayer(sessionId) : null;
    }

    public boolean isValidGameAction(String sessionId, String action) {
        GameRoom gameRoom = getGameRoomBySession(sessionId);
        if (gameRoom == null || gameRoom.getGame() == null) {
            return false;
        }
        return gameRoom.getStatus() == GameRoom.GameRoomStatus.IN_PROGRESS;
    }
}