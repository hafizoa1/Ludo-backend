package com.ludo.ludo_server.game.connection;


import com.ludo.ludo_server.game.Game;
import com.ludo.ludo_server.game.input.InputProvider;
import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import com.ludo.ludo_server.player.HumanPlayer;
import com.ludo.ludo_server.player.Player;
import com.ludo.ludo_server.player.PlayerColor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * GameManager - Central orchestrator for multiplayer game management
 *
 * Purpose: Handles all aspects of multiplayer game lifecycle and player management.
 * Acts as the main entry point for game-related operations from the WebSocket layer.
 *
 * Key Responsibilities:
 * - Create new multiplayer game rooms
 * - Handle players joining/leaving games
 * - Manage multiple concurrent games
 * - Assign player colors and IDs automatically
 * - Start games when enough players have joined
 * - Validate game actions and player permissions
 *
 * Data Management:
 * - Maintains map of all active GameRoom instances
 * - Uses SessionMapper to link WebSocket sessions to games
 * - Generates unique game IDs for room identification
 *
 * Used by: GameMessageController for all multiplayer game operations
 *
 * Why needed: Centralizes complex multiplayer logic, making the WebSocket
 * message controller thin and focused only on message routing.
 */

@Component
public class GameManager {

    @Autowired
    private GameIdGenerator gameIdGenerator;

    @Autowired
    private SessionMapper sessionMapper;

    private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private static final int DEFAULT_MAX_PLAYERS = 2;

    // 2-player setup: each player gets 2 colors (like original GameConfig)
    private static final List<PlayerColor> PLAYER1_COLORS = List.of(PlayerColor.RED, PlayerColor.YELLOW);
    private static final List<PlayerColor> PLAYER2_COLORS = List.of(PlayerColor.BLUE, PlayerColor.GREEN);

    public GameResponse createGame(String sessionId) {
        String gameId = gameIdGenerator.generateGameId();
        GameRoom gameRoom = new GameRoom(gameId, DEFAULT_MAX_PLAYERS);

        // Add creator as first player (Red + Yellow colors)
        Player creator = new HumanPlayer("player1", "Player 1", PLAYER1_COLORS);
        gameRoom.addPlayer(sessionId, creator);

        gameRooms.put(gameId, gameRoom);
        sessionMapper.addSessionToGame(sessionId, gameId);

        return GameResponse.success("GAME_CREATED",
                "Game " + gameId + " created. Waiting for players (1/" + DEFAULT_MAX_PLAYERS + ")",
                null);  // Pass null instead of Map for now
    }

    public GameResponse joinGame(String sessionId, String gameId) {
        GameRoom gameRoom = gameRooms.get(gameId);

        if (gameRoom == null) {
            return GameResponse.error("GAME_NOT_FOUND", "Game " + gameId + " not found");
        }

        if (!gameRoom.canJoin()) {
            return GameResponse.error("GAME_FULL", "Game " + gameId + " is full or already started");
        }

        // Create player with appropriate colors (Blue + Green for second player)
        List<PlayerColor> playerColors = gameRoom.getSessionIds().size() == 0 ? PLAYER1_COLORS : PLAYER2_COLORS;
        int playerNumber = gameRoom.getSessionIds().size() + 1;
        Player player = new HumanPlayer("player" + playerNumber, "Player " + playerNumber, playerColors);

        gameRoom.addPlayer(sessionId, player);
        sessionMapper.addSessionToGame(sessionId, gameId);

        int currentPlayers = gameRoom.getSessionIds().size();
        String message = "Joined game " + gameId + ". Waiting for players (" + currentPlayers + "/" + DEFAULT_MAX_PLAYERS + ")";

        // Check if game is ready to start
        if (gameRoom.isReady()) {
            startGame(gameRoom);
            message = "All players joined! Game starting...";
        }

        return GameResponse.success("JOINED_GAME", message, null);  // Pass null for now - should be game sate
    }

    public GameResponse leaveGame(String sessionId) {
        String gameId = sessionMapper.getGameId(sessionId);

        if (gameId == null) {
            return GameResponse.error("NO_GAME", "You are not in any game");
        }

        GameRoom gameRoom = gameRooms.get(gameId);
        if (gameRoom != null) {
            gameRoom.removePlayer(sessionId);

            // Remove empty games
            if (gameRoom.getSessionIds().isEmpty()) {
                gameRooms.remove(gameId);
            }
        }

        sessionMapper.removeSession(sessionId);
        return GameResponse.success("LEFT_GAME", "Left game " + gameId, null);
    }

    private void startGame(GameRoom gameRoom) {
        List<Player> players = new ArrayList<>(gameRoom.getSessionToPlayer().values());

        // Create a dummy InputProvider for multiplayer (won't be used)
        InputProvider dummyInput = new InputProvider() {
            public int getChoice(int min, int max, String prompt) { return 0; }
            public String getName(String prompt) { return ""; }
            public void sendMessage(String message) {}
            public void waitForInput(String prompt) {}
        };

        Game game = new Game(players, dummyInput); // Game constructor needs InputProvider
        gameRoom.setGame(game);
        gameRoom.setStatus(GameRoom.GameRoomStatus.IN_PROGRESS);
    }

    public GameRoom getGameRoom(String gameId) {
        return gameRooms.get(gameId);
    }

    public GameRoom getGameRoomBySession(String sessionId) {
        String gameId = sessionMapper.getGameId(sessionId);
        return gameId != null ? gameRooms.get(gameId) : null;
    }

    public Player getPlayerBySession(String sessionId) {
        GameRoom gameRoom = getGameRoomBySession(sessionId);
        return gameRoom != null ? gameRoom.getPlayer(sessionId) : null;
    }

    public boolean isValidGameAction(String sessionId, String action) {
        GameRoom gameRoom = getGameRoomBySession(sessionId);
        if (gameRoom == null || gameRoom.getGame() == null) {
            return false;
        }

        // Add turn validation logic here
        return gameRoom.getStatus() == GameRoom.GameRoomStatus.IN_PROGRESS;
    }
}