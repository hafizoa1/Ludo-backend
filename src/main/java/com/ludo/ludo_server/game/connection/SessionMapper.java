package com.ludo.ludo_server.game.connection;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SessionMapper {

    private final Map<String, String> sessionToGame = new ConcurrentHashMap<>();
    private final Map<String, List<String>> gameToSessions = new ConcurrentHashMap<>();

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
}
