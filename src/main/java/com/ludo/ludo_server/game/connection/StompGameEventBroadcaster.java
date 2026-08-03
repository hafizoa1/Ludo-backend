package com.ludo.ludo_server.game.connection;

import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class StompGameEventBroadcaster { //dDELETE NORMAL GAME EVENT BROADCASTER

    private static final Logger logger = LoggerFactory.getLogger(StompGameEventBroadcaster.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // Broadcast to all players in a game
    public void broadcastToGame(String gameId, GameResponse response) {
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/events", response);
        logger.debug("Broadcast to game {}: {} - {}", gameId, response.getType(), response);
    }

    public void sendToSession(String sessionId, GameResponse response) {
        sendToPlayer(sessionId, response); // Delegate to existing method
    }

    // Send to specific player
    public void sendToPlayer(String sessionId, GameResponse response) {
        String destination = "/user/" + sessionId + "/queue/response";
        logger.debug("[BROADCAST DEBUG] Sending message to player. Destination: {}, SessionId: {}, Response Type: {}, " +
                        "Success: {}, Message: {}, Error: {}, Full Response: {}",
                destination, sessionId, response.getType(), response.isSuccess(), response.getMessage(),
                response.getError(), response);

        messagingTemplate.convertAndSendToUser(sessionId, "/queue/response", response);
    }

}