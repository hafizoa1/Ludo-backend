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
    }

    public void sendToSession(String sessionId, GameResponse response) {
        sendToPlayer(sessionId, response); // Delegate to existing method
    }

    // Send to specific player
    public void sendToPlayer(String sessionId, GameResponse response) {
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/response", response);
        System.out.println("📤 Send to player " + sessionId + ": " + response.getType());
    }

    // Broadcast dice results
    public void broadcastDiceRoll(String gameId, GameResponse response) {
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/dice", response);
    }

    // Broadcast move results
    public void broadcastMove(String gameId, GameResponse response) {
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/moves", response);
    }

    // Broadcast turn changes
    public void broadcastTurnChange(String gameId, GameResponse response) {
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/turns", response);
    }

    // Broadcast game state updates
    public void broadcastGameState(String gameId, GameResponse response) {
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/state", response);
    }
}