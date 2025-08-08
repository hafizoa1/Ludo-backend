package com.ludo.ludo_server.game.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludo.ludo_server.game.connection.GameEventBroadcaster;
import com.ludo.ludo_server.game.websocket.controller.GameMessageController;
import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@AllArgsConstructor
public class GameWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private GameMessageController messageController;

    @Autowired
    private final GameEventBroadcaster broadcaster;

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        System.out.println("🔗 WebSocket connected: " + sessionId);

        // Register session for broadcasting
        broadcaster.registerSession(sessionId, session);

        GameResponse response = GameResponse.success("CONNECTED",
                "Connected. Send CREATE_GAME to create or JOIN_GAME with gameId to join", null);
        sendResponse(session, response);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String action = message.getPayload();

        System.out.println("📨 [" + sessionId + "] Received: " + action);

        // Delegate to controller
        GameResponse response = messageController.handleMessage(sessionId, action, session);
        sendResponse(session, response);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("🚨 WebSocket error for " + session.getId() + ": " + exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String sessionId = session.getId();
        messageController.removeGame(sessionId); // Clean up via controller
        System.out.println("❌ WebSocket disconnected: " + sessionId);
    }


    // ========== RESPONSE HANDLING ==========

    private void sendResponse(WebSocketSession session, GameResponse response) throws Exception {
        String json = objectMapper.writeValueAsString(response);
        session.sendMessage(new TextMessage(json));

        System.out.println("📤 [" + session.getId() + "] Sent: " + response.getType());
    }
}
