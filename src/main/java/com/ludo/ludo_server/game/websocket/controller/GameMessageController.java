package com.ludo.ludo_server.game.websocket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludo.ludo_server.game.Game;
import com.ludo.ludo_server.game.connection.GameEventBroadcaster;
import com.ludo.ludo_server.game.connection.GameManager;
import com.ludo.ludo_server.game.connection.GameRoom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;


import java.util.Map;

@Component
public class GameMessageController { // WHEN PLAYER 2 JOINEDS IT IS NOT BROADCSA=ASTED

    @Autowired
    private GameManager gameManager;

    @Autowired
    private GameEventBroadcaster broadcaster;

    // Remove old sessionGames Map - GameManager handles storage now

    public void removeGame(String sessionId) {
        gameManager.leaveGame(sessionId);
    }

    public GameResponse handleMessage(String sessionId, String action, WebSocketSession session) {
        // Parse JSON messages for more complex actions
        if (action.startsWith("{")) {
            return handleJsonMessage(sessionId, action, session);
        }

        // Handle simple string actions
        switch (action.toUpperCase()) {
            case "CREATE_GAME":
                return gameManager.createGame(sessionId);

            case "ROLL_DICE":
                return handleDiceRoll(sessionId);

            case "GET_GAME_STATE":
                return handleGetGameState(sessionId);

            default:
                return GameResponse.error("UNKNOWN_ACTION", "Unknown action: " + action);
        }
    }

    private GameResponse handleJsonMessage(String sessionId, String jsonAction, WebSocketSession session) {
        try {
            // Parse JSON for complex actions like JOIN_GAME
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> actionMap = mapper.readValue(jsonAction, Map.class);

            String action = (String) actionMap.get("action");

            switch (action.toUpperCase()) {
                case "JOIN_GAME":
                    String gameId = (String) actionMap.get("gameId");
                    return gameManager.joinGame(sessionId, gameId);

                default:
                    return GameResponse.error("UNKNOWN_ACTION", "Unknown JSON action: " + action);
            }

        } catch (Exception e) {
            return GameResponse.error("INVALID_JSON", "Invalid JSON message: " + e.getMessage());
        }
    }

    private GameResponse handleDiceRoll(String sessionId) {
        GameRoom gameRoom = gameManager.getGameRoomBySession(sessionId);

        if (gameRoom == null || gameRoom.getGame() == null) {
            return GameResponse.error("NO_GAME", "No active game found");
        }

        // Roll dice
        Game game = gameRoom.getGame();
        game.getDice().roll();

        String playerName = gameRoom.getPlayer(sessionId).getPlayerName();
        int die1 = game.getDice().getDie1();
        int die2 = game.getDice().getDie2();

        // Broadcast to ALL players that this player rolled dice
        broadcaster.broadcastToGame(gameRoom.getGameId(),
                GameResponse.success("DICE_ROLLED",
                        playerName + " rolled " + die1 + " and " + die2, null));

        // Don't return separate response - the broadcast IS the response
        return GameResponse.success("DICE_ROLL_PROCESSED", "Dice roll broadcast to all players", null);
    }

    private GameResponse handleGetGameState(String sessionId) {
        GameRoom gameRoom = gameManager.getGameRoomBySession(sessionId);

        if (gameRoom == null || gameRoom.getGame() == null) {
            return GameResponse.error("NO_GAME", "No active game found");
        }

        // TODO: Return full game state
        return GameResponse.success("GAME_STATE", "Current game state", null);
    }
}