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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.ludo.ludo_server.game.websocket.controller.ResponseType.*;


@Component
public class GameManager {

    private static final Logger logger = LoggerFactory.getLogger(GameManager.class);

    private final GameIdGenerator gameIdGenerator;
    private final SessionMapper sessionMapper;
    private final StompGameEventBroadcaster broadcaster;
    private final long disconnectTimeoutSeconds;
    private final Executor turnExecutor;

    public GameManager(GameIdGenerator gameIdGenerator, SessionMapper sessionMapper,
                        StompGameEventBroadcaster broadcaster,
                        @Value("${game.disconnect-timeout-seconds:30}") long disconnectTimeoutSeconds,
                        Executor gameTurnExecutor) {
        this.gameIdGenerator = gameIdGenerator;
        this.sessionMapper = sessionMapper;
        this.broadcaster = broadcaster;
        this.disconnectTimeoutSeconds = disconnectTimeoutSeconds;
        this.turnExecutor = gameTurnExecutor;
    }

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

    // Note: the playerId parameter here is the persistent client id (from the
    // request, tracked by SessionMapper for reconnection) - a different value
    // from the domain Player's own getPlayerId() constructed below ("player1"),
    // which only means "this player's identity within this one Game instance".
    public GameResponse createGame(String sessionId, String playerId) {
        String gameId = gameIdGenerator.generateGameId();
        GameRoom gameRoom = new GameRoom(gameId, DEFAULT_MAX_PLAYERS, broadcaster, turnExecutor);

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
            logger.info("Player {} reconnecting to game {}", playerId, gameId);

            // Update the session with new WebSocket sessionId
            sessionMapper.updatePlayerSession(playerId, sessionId);

            // Cancel any pending disconnect timeout
            ScheduledFuture<?> disconnectTask = disconnectTasks.remove(playerId);
            if (disconnectTask != null) {
                disconnectTask.cancel(false);
                logger.debug("Cancelled disconnect timer for player: {}", playerId);
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

        logger.debug("After join: {} players, isReady: {}", currentPlayers, gameRoom.isReady());

        if (gameRoom.isReady()) {
            logger.info("Starting game {}", gameId);
            gameRoom.startGame();
            message = "All players joined! Game starting...";
        }

        // Notify the room a new player joined - reconnections get their own
        // distinct message above instead, so this only fires for genuinely new players.
        broadcaster.broadcastToGame(gameId, GameResponse.success(PLAYER_JOINED, "New player joined", null));

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
                    if (game.getWinner() != null) {
                        handleGameOver(gameId, game);
                    }
                } catch (PlayerTimeoutException e) {
                    // Player timed out - end the game
                    logger.warn("Player timeout detected: {}", e.getPlayerName());
                    handlePlayerTimeout(gameId, e);
                } catch (Exception e) {
                    logger.error("Error continuing game after dice roll: {}", e.getMessage(), e);
                }
            }, turnExecutor);

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

                return GameResponse.success(CHOICE_RECEIVED, "Choice processed", null);
            }

            return GameResponse.error(INVALID_GAME, "Not a multiplayer game");

        } catch (Exception e) {
            logger.error("Error in handlePlayerChoice: {}", e.getMessage(), e);
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
                logger.debug("Cancelled disconnect timer for leaving player: {}", playerSession.getPlayerId());
            }
        }
    }

    /**
     * Handle player disconnect - called by WebSocketEventListener.
     * Marks the session disconnected and starts the reconnection timer in one
     * call, so callers can't forget the first half of this.
     */
    public void handlePlayerDisconnect(String sessionId) {
        logger.debug("[DISCONNECT DEBUG] handlePlayerDisconnect called with sessionId: {}", sessionId);

        PlayerSession playerSession = sessionMapper.findPlayerSessionBySessionId(sessionId);

        if (playerSession == null) {
            logger.debug("[DISCONNECT DEBUG] No player session found for sessionId: {}", sessionId);
            return;
        }

        sessionMapper.markPlayerDisconnected(sessionId);

        String gameId = playerSession.getGameId();
        String playerId = playerSession.getPlayerId();
        String playerName = playerSession.getDisplayName();

        logger.debug("[DISCONNECT DEBUG] Handling disconnect for: Player ID: {}, Player Name: {}, Game ID: {}",
                playerId, playerName, gameId);

        // Get the game room
        GameRoom gameRoom = getGameRoom(gameId);
        if (gameRoom == null) {
            logger.debug("[DISCONNECT DEBUG] Game room not found: {}", gameId);
            return;
        }

        // Notify opponent that player disconnected
        String message = playerName + " disconnected. Waiting " + disconnectTimeoutSeconds + " seconds for reconnection...";
        logger.debug("[DISCONNECT DEBUG] Broadcasting disconnect message to game: {}", message);
        broadcaster.broadcastToGame(gameId,
            GameResponse.success(GAME_MESSAGE, message, null));

        // Start disconnect timer
        logger.debug("[DISCONNECT DEBUG] Scheduling {}s disconnect timeout timer... current time: {}, fires at: {}",
                disconnectTimeoutSeconds, System.currentTimeMillis(), System.currentTimeMillis() + disconnectTimeoutSeconds * 1000);

        ScheduledFuture<?> disconnectTask = scheduler.schedule(() -> {
            logger.debug("[DISCONNECT DEBUG] Disconnect timeout fired! Calling handleDisconnectTimeout()");
            handleDisconnectTimeout(playerId, gameId);
        }, disconnectTimeoutSeconds, TimeUnit.SECONDS);

        // Store the task so we can cancel it if player reconnects
        disconnectTasks.put(playerId, disconnectTask);

        logger.debug("[DISCONNECT DEBUG] Disconnect timer scheduled and stored for player: {}", playerId);
    }

    /**
     * Called after 30 seconds if player hasn't reconnected
     */
    private void handleDisconnectTimeout(String playerId, String gameId) {
        logger.debug("[TIMEOUT DEBUG] handleDisconnectTimeout() called for player: {}, game: {}, current time: {}",
                playerId, gameId, System.currentTimeMillis());

        // Check if player is still disconnected
        PlayerSession playerSession = sessionMapper.findPlayerSessionByPlayerId(playerId);

        if (playerSession == null || playerSession.getStatus() == PlayerStatus.CONNECTED) {
            // Player reconnected or session was cleaned up - do nothing
            logger.info("Player {} reconnected before timeout", playerId);
            return;
        }

        logger.info("Player {} did not reconnect - ending game", playerId);

        // Player still disconnected - end game and declare opponent winner
        GameRoom gameRoom = getGameRoom(gameId);
        if (gameRoom != null) {
            gameRoom.endByDisconnect(playerSession.getCurrentSessionId(), playerSession.getDisplayName());
            endGame(gameId, gameRoom);
        } else {
            // Game room already removed
            sessionMapper.removePlayerSession(playerId);
        }

        // Clean up the disconnect task (endGame already does this when gameRoom
        // was found, but this session's task is keyed by playerId either way)
        disconnectTasks.remove(playerId);

        logger.info("Cleaned up game and player session for disconnect timeout");
    }

    /**
     * Handle a game that ended normally via a player winning.
     */
    private void handleGameOver(String gameId, Game game) {
        GameRoom gameRoom = getGameRoom(gameId);
        if (gameRoom == null) {
            logger.warn("Game room not found for completed game: {}", gameId);
            return;
        }

        gameRoom.endByWin(game.getWinner());
        endGame(gameId, gameRoom);

        logger.info("Game {} ended. Winner: {}", gameId, game.getWinner());
    }

    /**
     * Handle player timeout - end game and declare opponent winner
     */
    private void handlePlayerTimeout(String gameId, PlayerTimeoutException timeoutException) {
        logger.warn("Handling timeout for player {} in game {}", timeoutException.getPlayerName(), gameId);

        GameRoom gameRoom = getGameRoom(gameId);
        if (gameRoom == null) {
            logger.warn("Game room not found: {}", gameId);
            return;
        }

        gameRoom.endByTimeout(timeoutException.getSessionId(), timeoutException.getPlayerName());
        endGame(gameId, gameRoom);

        logger.info("Game {} ended due to timeout.", gameId);
    }

    /**
     * Shared cleanup once a GameRoom has finished announcing why it ended:
     * remove it from the registry and release every player's session plus
     * any pending disconnect timer. Every way a game can end funnels through
     * here, so there's exactly one place responsible for not leaking a game.
     */
    private void endGame(String gameId, GameRoom gameRoom) {
        gameRooms.remove(gameId);

        for (String sid : gameRoom.getSessionIds()) {
            PlayerSession session = sessionMapper.findPlayerSessionBySessionId(sid);
            if (session != null) {
                ScheduledFuture<?> task = disconnectTasks.remove(session.getPlayerId());
                if (task != null) {
                    task.cancel(false);
                }
                sessionMapper.removePlayerSession(session.getPlayerId());
            }
        }
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
}