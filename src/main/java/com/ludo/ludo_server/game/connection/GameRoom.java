package com.ludo.ludo_server.game.connection;


/**
 * GameRoom - Represents a single multiplayer game lobby/session
 *
 * Purpose: Manages the lifecycle of one multiplayer game from creation to completion.
 * Tracks all players in the room, their connection status, and the game state.
 *
 * Responsibilities:
 * - Track which players (sessionIds) are in this specific game
 * - Map sessionIds to their corresponding Player objects
 * - Manage room status (waiting, ready, in progress, finished)
 * - Enforce player limits (e.g., max 2 players for multiplayer Ludo)
 * - Hold reference to the actual Game instance once started
 *
 * Lifecycle: WAITING_FOR_PLAYERS → READY_TO_START → IN_PROGRESS → FINISHED/ABANDONED
 *
 * Used by: GameManager to organize multiple concurrent games
 *
 * Why needed: Multiple games can run simultaneously, each needs its own isolated
 * player management and state tracking.
 */


import com.ludo.ludo_server.game.Game;
import com.ludo.ludo_server.game.input.MultiplayerInputProvider;
import com.ludo.ludo_server.game.state.GameState;
import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import com.ludo.ludo_server.player.Player;
import com.ludo.ludo_server.player.PlayerColor;
import lombok.Data;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import static com.ludo.ludo_server.game.websocket.controller.ResponseType.GAME_ENDED;
import static com.ludo.ludo_server.game.websocket.controller.ResponseType.GAME_ENDED_TIMEOUT;
import static com.ludo.ludo_server.game.websocket.controller.ResponseType.GAME_STARTED;

@Data
public class GameRoom {

    private static final Logger logger = LoggerFactory.getLogger(GameRoom.class);

    private final String gameId;
    private final List<String> sessionIds;
    private final Map<String, Player> sessionToPlayer;
    @Getter
    private Game game;

    private TurnManager turnManager;
    private GameRoomStatus status;
    private final int maxPlayers;
    private StompGameEventBroadcaster broadcaster;
    private Executor turnExecutor;

    public GameRoom(String gameId, int maxPlayers, StompGameEventBroadcaster broadcaster, Executor turnExecutor) {
        this.gameId = gameId;
        this.maxPlayers = maxPlayers;
        this.broadcaster = broadcaster;
        this.turnExecutor = turnExecutor;
        this.sessionIds = new ArrayList<>();
        this.sessionToPlayer = new ConcurrentHashMap<>();
        this.status = GameRoomStatus.WAITING_FOR_PLAYERS;
    }

    public boolean canJoin() {
        return sessionIds.size() < maxPlayers && status == GameRoomStatus.WAITING_FOR_PLAYERS;
    }

    public void addPlayer(String sessionId, Player player) {
        if (canJoin()) {
            sessionIds.add(sessionId);
            sessionToPlayer.put(sessionId, player);

            if (sessionIds.size() == maxPlayers) {
                status = GameRoomStatus.READY_TO_START;
            }
        }
    }

    public void removePlayer(String sessionId) {
        sessionIds.remove(sessionId);
        sessionToPlayer.remove(sessionId);

        if (sessionIds.isEmpty()) {
            status = GameRoomStatus.ABANDONED;
        } else if (status == GameRoomStatus.READY_TO_START) {
            status = GameRoomStatus.WAITING_FOR_PLAYERS;
        }
    }

    public void reconnectPlayer(String oldSessionId, String newSessionId) {
        // Get the player associated with old session
        Player player = sessionToPlayer.remove(oldSessionId);

        if (player != null) {
            // Update sessionIds list
            int index = sessionIds.indexOf(oldSessionId);
            if (index != -1) {
                sessionIds.set(index, newSessionId);
            }

            // Re-map to new session
            sessionToPlayer.put(newSessionId, player);

            logger.info("GameRoom: Reconnected player from {} to {}", oldSessionId, newSessionId);
        }
    }

    // Enhanced startGame method
    public void startGame() {
        List<Player> players = new ArrayList<>(sessionToPlayer.values());

        // Pass 'this' (the GameRoom) to the input provider
        MultiplayerInputProvider inputProvider = new MultiplayerInputProvider(this.gameId, this.broadcaster, this);

        this.game = new Game(players, inputProvider, broadcaster, gameId);
        this.status = GameRoomStatus.IN_PROGRESS;

        // Rest stays the same...
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(500);
                GameState initialState = game.getGameState();
                broadcaster.broadcastToGame(gameId,
                        GameResponse.success(GAME_STARTED, "Game started", initialState));
                game.startGame();
            } catch (Exception e) {
                logger.error("Game error: {}", e.getMessage(), e);
                status = GameRoomStatus.FINISHED;
            }
        }, turnExecutor);

        logger.info("Game started for room: {}", gameId);
    }

    /**
     * End this room because a player legitimately won.
     */
    public void endByWin(String winnerName) {
        status = GameRoomStatus.FINISHED;
        broadcaster.broadcastToGame(gameId,
                GameResponse.success(GAME_ENDED, winnerName + " has won the game!"));
    }

    /**
     * End this room because a player didn't respond to a choice in time -
     * the other player wins by forfeit.
     */
    public void endByTimeout(String timedOutSessionId, String timedOutPlayerName) {
        announceForfeit(timedOutSessionId, timedOutPlayerName, false);
    }

    /**
     * End this room because a player disconnected and never reconnected -
     * the other player wins by forfeit.
     */
    public void endByDisconnect(String disconnectedSessionId, String disconnectedPlayerName) {
        announceForfeit(disconnectedSessionId, disconnectedPlayerName, true);
    }

    private void announceForfeit(String forfeitingSessionId, String forfeitingPlayerName, boolean wasDisconnect) {
        status = GameRoomStatus.FINISHED;

        String winnerSessionId = null;
        String winnerName = "Opponent";
        for (String sid : sessionIds) {
            if (!sid.equals(forfeitingSessionId)) {
                winnerSessionId = sid;
                Player winner = getPlayer(sid);
                if (winner != null) {
                    winnerName = winner.getPlayerName();
                }
                break;
            }
        }

        if (winnerSessionId != null) {
            if (wasDisconnect) {
                // Disconnect messages embed colors (format: "color1,color2|message")
                // so the frontend can show which pieces belonged to whom.
                String winnerColors = colorsOf(getPlayer(winnerSessionId));
                String forfeitingColors = colorsOf(getPlayer(forfeitingSessionId));
                broadcaster.sendToPlayer(winnerSessionId,
                        GameResponse.success(GAME_ENDED_TIMEOUT,
                                winnerColors + "|" + forfeitingPlayerName + " disconnected and did not reconnect. Game has ended - You win!"));
                broadcaster.sendToPlayer(forfeitingSessionId,
                        GameResponse.error(GAME_ENDED_TIMEOUT,
                                forfeitingColors + "|You disconnected and did not reconnect. Game has ended - " + winnerName + " wins."));
            } else {
                broadcaster.sendToPlayer(winnerSessionId,
                        GameResponse.success(GAME_ENDED_TIMEOUT,
                                forfeitingPlayerName + " is unresponsive. Game has ended - You win!"));
                broadcaster.sendToPlayer(forfeitingSessionId,
                        GameResponse.error(GAME_ENDED_TIMEOUT,
                                "You were unresponsive. Game has ended - " + winnerName + " wins."));
            }
        }

        String reasonPhrase = wasDisconnect ? "disconnected." : "is unresponsive.";
        broadcaster.broadcastToGame(gameId,
                GameResponse.success(GAME_ENDED_TIMEOUT, forfeitingPlayerName + " " + reasonPhrase + " Game has ended."));
    }

    private String colorsOf(Player player) {
        if (player == null) {
            return "";
        }
        List<PlayerColor> colors = player.getColors();
        return colors.get(0).toString().toLowerCase() + "," + colors.get(1).toString().toLowerCase();
    }

    public Player getPlayer(String sessionId) {
        return sessionToPlayer.get(sessionId);
    }

    public String getSessionIdForPlayer(Player player) {
        for (Map.Entry<String, Player> entry : sessionToPlayer.entrySet()) {
            if (entry.getValue().equals(player)) {
                return entry.getKey();
            }
        }
        return null; // Player not found
    }

    public boolean isReady() {
        return status == GameRoomStatus.READY_TO_START;
    }

    public boolean isFull() {
        return sessionIds.size() >= maxPlayers;
    }

    public enum GameRoomStatus {
        WAITING_FOR_PLAYERS,
        READY_TO_START,
        IN_PROGRESS,
        FINISHED,
        ABANDONED
    }
}