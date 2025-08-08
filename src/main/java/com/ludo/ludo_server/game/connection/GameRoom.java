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
import com.ludo.ludo_server.player.Player;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class GameRoom {

    private final String gameId;
    private final List<String> sessionIds;
    private final Map<String, Player> sessionToPlayer;
    private Game game;
    private GameRoomStatus status;
    private final int maxPlayers;

    public GameRoom(String gameId, int maxPlayers) {
        this.gameId = gameId;
        this.maxPlayers = maxPlayers;
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

    public Player getPlayer(String sessionId) {
        return sessionToPlayer.get(sessionId);
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