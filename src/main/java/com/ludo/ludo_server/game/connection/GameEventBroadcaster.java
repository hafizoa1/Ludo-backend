package com.ludo.ludo_server.game.connection;

/**
 * GameEventBroadcaster - Handles sending messages to all players in a game
 *
 * Purpose: When one player performs an action (like rolling dice), all other players
 * in the same game need to see that action. This class manages broadcasting messages
 * to multiple WebSocket sessions that belong to the same game room.
 *
 * Key Operations:
 * - Broadcast to all players in a specific game
 * - Send message to specific player only
 * - Handle WebSocket session management and error cases
 *
 * Used by: GameMessageController when game actions need to be shared with all players
 *
 * Why needed: Multiplayer games require all players to stay synchronized with
 * the current game state and see other players' actions in real-time.
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class GameEventBroadcaster {

    @Autowired
    private SessionMapper sessionMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    // Register WebSocket session when player connects
    public void registerSession(String sessionId, WebSocketSession session) {
        activeSessions.put(sessionId, session);
    }

    // Remove WebSocket session when player disconnects
    public void unregisterSession(String sessionId) {
        activeSessions.remove(sessionId);
    }

    // Broadcast message to all players in a specific game
    public void broadcastToGame(String gameId, GameResponse response) {
        List<String> sessionIds = sessionMapper.getSessionsInGame(gameId);

        for (String sessionId : sessionIds) {
            sendToSession(sessionId, response);
        }
    }

    // Broadcast to all players in game EXCEPT the sender
    public void broadcastToOthers(String gameId, String excludeSessionId, GameResponse response) {
        List<String> sessionIds = sessionMapper.getSessionsInGame(gameId);

        for (String sessionId : sessionIds) {
            if (!sessionId.equals(excludeSessionId)) {
                sendToSession(sessionId, response);
            }
        }
    }

    // Send message to specific player only
    public void sendToSession(String sessionId, GameResponse response) {
        WebSocketSession session = activeSessions.get(sessionId);

        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(response);
                session.sendMessage(new TextMessage(json));
                System.out.println("📤 [" + sessionId + "] Broadcast: " + response.getType());
            } catch (Exception e) {
                System.err.println("Failed to send message to session " + sessionId + ": " + e.getMessage());
                // Remove failed session
                activeSessions.remove(sessionId);
            }
        }
    }

    // Notify all players when someone joins the game
    public void broadcastPlayerJoined(String gameId, String joinedPlayerName) {
        GameResponse response = GameResponse.success("PLAYER_JOINED",
                joinedPlayerName + " joined the game", null);
        broadcastToGame(gameId, response);
    }

    // Notify all players when someone leaves the game
    public void broadcastPlayerLeft(String gameId, String leftPlayerName) {
        GameResponse response = GameResponse.success("PLAYER_LEFT",
                leftPlayerName + " left the game", null);
        broadcastToGame(gameId, response);
    }
}
