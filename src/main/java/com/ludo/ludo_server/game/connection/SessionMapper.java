package com.ludo.ludo_server.game.connection;

import com.ludo.ludo_server.player.Player;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SessionMapper - Extended to support persistent player identity and reconnection
 *
 * Now tracks BOTH:
 * 1. sessionId → gameId (original functionality)
 * 2. playerId → PlayerSession (NEW - for reconnection support)
 */
@Component
public class SessionMapper {

    // ========== ORIGINAL MAPPINGS ==========
    private final Map<String, String> sessionToGame = new ConcurrentHashMap<>();
    private final Map<String, List<String>> gameToSessions = new ConcurrentHashMap<>();

    // ========== NEW MAPPINGS FOR PERSISTENT IDENTITY ==========
    // playerId → PlayerSession (persistent across reconnects)
    private final Map<String, PlayerSession> playerSessions = new ConcurrentHashMap<>();

    // sessionId → playerId (for quick lookup on disconnect)
    private final Map<String, String> sessionToPlayer = new ConcurrentHashMap<>();


    // ========== ORIGINAL METHODS (UNCHANGED) ==========

    public void addSessionToGame(String sessionId, String gameId) {
        sessionToGame.put(sessionId, gameId);
        gameToSessions.computeIfAbsent(gameId, k -> new CopyOnWriteArrayList<>()).add(sessionId);
    }

    public void removeSession(String sessionId) {
        String gameId = sessionToGame.remove(sessionId);
        if (gameId != null) {
            List<String> sessions = gameToSessions.get(gameId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    gameToSessions.remove(gameId);
                }
            }
        }

        // NEW: Also remove from player mappings
        String playerId = sessionToPlayer.remove(sessionId);
        if (playerId != null) {
            playerSessions.remove(playerId);
            System.out.println("🗑️ Removed player session: " + playerId);
        }
    }

    public String getGameId(String sessionId) {
        return sessionToGame.get(sessionId);
    }

    public List<String> getSessionsInGame(String gameId) {
        return gameToSessions.getOrDefault(gameId, List.of());
    }

    public boolean isSessionInGame(String sessionId, String gameId) {
        return gameId.equals(sessionToGame.get(sessionId));
    }


    // ========== NEW METHODS FOR PLAYER SESSION MANAGEMENT ==========

    /**
     * Create a new player session
     */
    public PlayerSession createPlayerSession(String playerId, String sessionId, String gameId, Player player) {
        PlayerSession newSession = new PlayerSession(playerId, sessionId, gameId, player);
        playerSessions.put(playerId, newSession);
        sessionToPlayer.put(sessionId, playerId);
        addSessionToGame(sessionId, gameId);

        System.out.println("✅ Created new session for player: " + playerId);
        return newSession;
    }

    /**
     * Update player session with new WebSocket sessionId (reconnection)
     */
    public PlayerSession updatePlayerSession(String playerId, String newSessionId) {
        PlayerSession existing = playerSessions.get(playerId);
        if (existing != null) {
            existing.updateSession(newSessionId);
            sessionToPlayer.put(newSessionId, playerId);
            addSessionToGame(newSessionId, existing.getGameId());

            System.out.println("✅ Updated session for player: " + playerId);
            return existing;
        }
        return null;
    }

    /**
     * Find session by playerId (for reconnection checks)
     */
    public PlayerSession findPlayerSessionByPlayerId(String playerId) {
        return playerSessions.get(playerId);
    }

    /**
     * Find session by current WebSocket sessionId (for disconnect handling)
     */
    public PlayerSession findPlayerSessionBySessionId(String sessionId) {
        String playerId = sessionToPlayer.get(sessionId);
        return playerId != null ? playerSessions.get(playerId) : null;
    }

    /**
     * Mark player as disconnected
     */
    public void markPlayerDisconnected(String sessionId) {
        PlayerSession session = findPlayerSessionBySessionId(sessionId);
        if (session != null) {
            session.markDisconnected();
            System.out.println("🔌 Marked player as disconnected: " + session.getPlayerId());
        }
    }

    /**
     * Mark player as left (intentional)
     */
    public void markPlayerLeft(String sessionId) {
        PlayerSession session = findPlayerSessionBySessionId(sessionId);
        if (session != null) {
            session.markLeft();
            System.out.println("👋 Marked player as left: " + session.getPlayerId());
        }
    }

    /**
     * Remove player session by playerId
     */
    public void removePlayerSession(String playerId) {
        PlayerSession session = playerSessions.remove(playerId);
        if (session != null) {
            sessionToPlayer.remove(session.getCurrentSessionId());
            removeSession(session.getCurrentSessionId()); // Also remove from old mappings
            System.out.println("🗑️ Removed player session: " + playerId);
        }
    }

    /**
     * Get all active players in a game
     */
    public long getActivePlayersInGame(String gameId) {
        return playerSessions.values().stream()
                .filter(session -> gameId.equals(session.getGameId()))
                .filter(PlayerSession::isConnected)
                .count();
    }
}
