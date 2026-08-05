package com.ludo.ludo_server.game.websocket;


import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(GameStompController.class);

    @Autowired
    private GameManager gameManager;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;


    // =============================================================================
    // GAME CREATION & JOINING
    // =============================================================================

    @MessageMapping("/game.create")
    @SendToUser("/queue/response")
    public GameResponse createGame(@RequestBody(required = false) Map<String, String> request, Principal user) {
        String sessionId = user.getName(); // STOMP provides session ID via Principal

        // Handle missing playerId for backward compatibility (temporary)
        String playerId = (request != null) ? request.get("playerId") : null;
        if (playerId == null || playerId.isEmpty()) {
            playerId = "temp-" + sessionId; // Use sessionId as fallback
            logger.warn("No playerId provided, using fallback: {}", playerId);
        }

        logger.debug("[{}] Create game request (playerId: {})", sessionId, playerId);

        return gameManager.createGame(sessionId, playerId);
    }

    @MessageMapping("/game.join")
    @SendToUser("/queue/response")
    public GameResponse joinGame(@RequestBody Map<String, String> request, Principal user) {
        String sessionId = user.getName();
        String gameId = request.get("gameId");
        String playerId = request.get("playerId");

        // Handle missing playerId for backward compatibility (temporary)
        if (playerId == null || playerId.isEmpty()) {
            playerId = "temp-" + sessionId; // Use sessionId as fallback
            logger.warn("No playerId provided, using fallback: {}", playerId);
        }

        logger.debug("[{}] Join game: {} (playerId: {})", sessionId, gameId, playerId);

        return gameManager.joinGame(sessionId, gameId, playerId);
    }

    @MessageMapping("/game.leave")
    @SendToUser("/queue/response")
    public GameResponse leaveGame(Principal user) {
        String sessionId = user.getName();
        logger.debug("[{}] Leave game request", sessionId);

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
        logger.debug("[{}] Roll dice request", sessionId);

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

        logger.debug("[{}] Player choice: {}", sessionId, choice);

        // Use your existing logic!
        GameResponse response = gameManager.handlePlayerChoice(sessionId, choice);

        // Send response to requesting player
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/response", response);
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
}

