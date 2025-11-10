package com.ludo.ludo_server.game.websocket;


import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import org.springframework.beans.factory.annotation.Autowired;
import com.ludo.ludo_server.game.connection.GameManager;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;

import java.security.Principal;
import java.util.Map;

import static com.ludo.ludo_server.game.websocket.controller.ResponseType.*;

@Controller
public class GameStompController {

    @Autowired
    private GameManager gameManager;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;


    // =============================================================================
    // GAME CREATION & JOINING
    // =============================================================================

    @MessageMapping("/game.create")
    @SendToUser("/queue/response")
    public GameResponse createGame(@RequestBody Map<String, String> request, Principal user) {
        String sessionId = user.getName(); // STOMP provides session ID via Principal
        String playerId = request.get("playerId");

        System.out.println("📨 [" + sessionId + "] Create game request (playerId: " + playerId + ")");

        if (playerId == null || playerId.isEmpty()) {
            return GameResponse.error(INVALID_GAME, "playerId is required");
        }

        return gameManager.createGame(sessionId, playerId);
    }

    @MessageMapping("/game.join")
    @SendToUser("/queue/response")
    public GameResponse joinGame(@RequestBody Map<String, String> request, Principal user) {
        String sessionId = user.getName();
        String gameId = request.get("gameId");
        String playerId = request.get("playerId");

        System.out.println("📨 [" + sessionId + "] Join game: " + gameId + " (playerId: " + playerId + ")");

        if (playerId == null || playerId.isEmpty()) {
            return GameResponse.error(INVALID_GAME, "playerId is required");
        }

        GameResponse response = gameManager.joinGame(sessionId, gameId, playerId);

        // If successful join, notify all players in the game
        if (response.isSuccess()) {
            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/players",
                    GameResponse.success(PLAYER_JOINED, "New player joined", response.getData()));
        }

        return response;
    }

    @MessageMapping("/game.leave")
    @SendToUser("/queue/response")
    public GameResponse leaveGame(Principal user) {
        String sessionId = user.getName();
        System.out.println("📨 [" + sessionId + "] Leave game request");

        // Mark player as intentionally left
        gameManager.markPlayerLeft(sessionId);

        // Call the existing leaveGame logic
        GameResponse response = gameManager.leaveGame(sessionId);

        return response;
    }

    // =============================================================================
    // DICE ROLLING
    // =============================================================================

    @MessageMapping("/game.roll")
    public void rollDice(Principal user) {
        String sessionId = user.getName();
        System.out.println("📨 [" + sessionId + "] Roll dice request");

        try {
            // Handle the dice roll
            GameResponse response = gameManager.handleDiceRoll(sessionId);

            // Send a personal response to the user about their roll request
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/response", response);
        } catch (Exception e) {
            // Handle any unexpected errors
            GameResponse errorResponse = GameResponse.error(ROLL_ERROR, "Unexpected error during dice roll");
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/response", errorResponse);
        }
    }

    // =============================================================================
    // PLAYER CHOICES
    // =============================================================================

    @MessageMapping("/game.choice")
    public void makeChoice(@RequestBody Map<String, Object> request, Principal user) {
        String sessionId = user.getName();
        int choice = (Integer) request.get("choice");

        System.out.println("📨 [" + sessionId + "] Player choice: " + choice);

        // Use your existing logic!
        GameResponse response = gameManager.handlePlayerChoice(sessionId, choice);

        // Send response to requesting player
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/response", response);

        // If move was made, broadcast updated game state
        if (response.isSuccess() && response.getData() != null) {
            String gameId = getGameIdForSession(sessionId);
            if (gameId != null) {
                messagingTemplate.convertAndSend("/topic/game/" + gameId + "/moves",
                        GameResponse.success(MOVE_EXECUTED, response.getMessage(), response.getData()));
            }
        }
    }

    // =============================================================================
    // GAME STATE REQUESTS
    // =============================================================================

    @MessageMapping("/game.state")
    @SendToUser("/queue/response")
    public GameResponse getGameState(Principal user) {
        String sessionId = user.getName();
        return gameManager.getGameState(sessionId);
    }

    // =============================================================================
    // HELPER METHODS
    // =============================================================================

    private String getGameIdForSession(String sessionId) {
        // Use your existing GameManager to get game ID
        var gameRoom = gameManager.getGameRoomBySession(sessionId);
        return gameRoom != null ? gameRoom.getGameId() : null;
    }
}

