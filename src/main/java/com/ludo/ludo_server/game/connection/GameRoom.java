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
import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.ludo.ludo_server.game.websocket.controller.ResponseType.GAME_STARTED;

@Data
public class GameRoom {

    private final String gameId;
    private final List<String> sessionIds;
    private final Map<String, Player> sessionToPlayer;
    @Getter
    private Game game;

    private TurnManager turnManager;
    private GameRoomStatus status;
    private final int maxPlayers;
    private StompGameEventBroadcaster broadcaster;

    // Updated constructor to take broadcaster
    public GameRoom(String gameId, int maxPlayers, StompGameEventBroadcaster broadcaster) {
        this.gameId = gameId;
        this.maxPlayers = maxPlayers;
        this.broadcaster = broadcaster;
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

            System.out.println("🔄 GameRoom: Reconnected player from " + oldSessionId + " to " + newSessionId);
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
                System.err.println("Game error: " + e.getMessage());
                status = GameRoomStatus.FINISHED;
            }
        });

        System.out.println("Game started for room: " + gameId);
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