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

        if (gameRoom.isReady()) {
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

            CompletableFuture.runAsync(() -> {
                try {
                    game.continueAfterDiceRoll();
                } catch (Exception e) {
                    System.err.println("Error continuing game after dice roll: " + e.getMessage());
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
        PlayerSession playerSession = sessionMapper.findPlayerSessionBySessionId(sessionId);

        if (playerSession == null) {
            System.out.println("⚠️ handlePlayerDisconnect: No player session found for sessionId: " + sessionId);
            return;
        }

        String gameId = playerSession.getGameId();
        String playerId = playerSession.getPlayerId();

        System.out.println("🔌 Handling disconnect for player: " + playerId + " in game: " + gameId);

        // Get the game room
        GameRoom gameRoom = getGameRoom(gameId);
        if (gameRoom == null) {
            System.out.println("⚠️ Game room not found: " + gameId);
            return;
        }

        // Notify opponent that player disconnected
        String message = playerSession.getDisplayName() + " disconnected. Waiting 30 seconds for reconnection...";
        broadcaster.broadcastToGame(gameId,
            GameResponse.success(GAME_MESSAGE, message, null));

        // Start 30-second timer
        ScheduledFuture<?> disconnectTask = scheduler.schedule(() -> {
            handleDisconnectTimeout(playerId, gameId);
        }, 30, TimeUnit.SECONDS);

        // Store the task so we can cancel it if player reconnects
        disconnectTasks.put(playerId, disconnectTask);

        System.out.println("⏰ Started 30-second disconnect timer for player: " + playerId);
    }

    /**
     * Called after 30 seconds if player hasn't reconnected
     */
    private void handleDisconnectTimeout(String playerId, String gameId) {
        System.out.println("⏰ Disconnect timeout reached for player: " + playerId);

        // Check if player is still disconnected
        PlayerSession playerSession = sessionMapper.findPlayerSessionByPlayerId(playerId);

        if (playerSession == null || playerSession.getStatus() == PlayerStatus.CONNECTED) {
            // Player reconnected or session was cleaned up - do nothing
            System.out.println("✅ Player " + playerId + " reconnected before timeout");
            return;
        }

        System.out.println("❌ Player " + playerId + " did not reconnect - ending game");

        // Player still disconnected - declare opponent winner
        GameRoom gameRoom = getGameRoom(gameId);
        if (gameRoom != null) {
            // Notify players that game ended due to disconnect
            String message = playerSession.getDisplayName() + " failed to reconnect. Game ended.";
            broadcaster.broadcastToGame(gameId,
                GameResponse.success(GAME_MESSAGE, message, null));

            // TODO: Determine winner (the other player who is still connected)
            // TODO: Update game state to ended

            // Clean up the game room
            gameRooms.remove(gameId);
        }

        // Remove player session
        sessionMapper.removePlayerSession(playerId);

        // Clean up the disconnect task
        disconnectTasks.remove(playerId);

        System.out.println("🗑️ Cleaned up game and player session for timeout");
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