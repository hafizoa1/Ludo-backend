package com.ludo.ludo_server.game.connection;

import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class StompGameEventBroadcaster { //dDELETE NORMAL GAME EVENT BROADCASTER

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // Broadcast to all players in a game
    public void broadcastToGame(String gameId, GameResponse response) {
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/events", response);
        System.out.println("📤 Broadcast to game " + gameId + ": " + response.getType());
        System.out.println(response);
        System.out.println("\n\n\n");
    }

    public void sendToSession(String sessionId, GameResponse response) {
        sendToPlayer(sessionId, response); // Delegate to existing method
    }

    // Send to specific player
    public void sendToPlayer(String sessionId, GameResponse response) {
        String destination = "/user/" + sessionId + "/queue/response";
        System.out.println("=====================================");
        System.out.println("📤 [BROADCAST DEBUG] SENDING MESSAGE TO PLAYER");
        System.out.println("   [BROADCAST DEBUG] Destination: " + destination);
        System.out.println("   [BROADCAST DEBUG] SessionId: " + sessionId);
        System.out.println("   [BROADCAST DEBUG] Response Type: " + response.getType());
        System.out.println("   [BROADCAST DEBUG] Success: " + response.isSuccess());
        System.out.println("   [BROADCAST DEBUG] Message: " + response.getMessage());
        System.out.println("   [BROADCAST DEBUG] Error: " + response.getError());
        System.out.println("   [BROADCAST DEBUG] Full Response: " + response);
        System.out.println("=====================================");

        messagingTemplate.convertAndSendToUser(sessionId, "/queue/response", response);
    }

}