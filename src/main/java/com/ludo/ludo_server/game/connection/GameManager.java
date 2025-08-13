package com.ludo.ludo_server.game.connection;


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

import com.ludo.ludo_server.game.Game;
import com.ludo.ludo_server.game.input.MultiplayerInputProvider;
import com.ludo.ludo_server.game.state.GameState;
import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import com.ludo.ludo_server.player.HumanPlayer;
import com.ludo.ludo_server.player.Player;
import com.ludo.ludo_server.player.PlayerColor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class GameManager {

    @Autowired
    private GameIdGenerator gameIdGenerator;

    @Autowired
    private SessionMapper sessionMapper;

    @Autowired
    private StompGameEventBroadcaster broadcaster;

    private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private static final int DEFAULT_MAX_PLAYERS = 2;

    // 2-player setup: each player gets 2 colors
    private static final List<PlayerColor> PLAYER1_COLORS = List.of(PlayerColor.RED, PlayerColor.YELLOW);
    private static final List<PlayerColor> PLAYER2_COLORS = List.of(PlayerColor.BLUE, PlayerColor.GREEN);

    // =============================================================================
    // GAME ROOM MANAGEMENT
    // =============================================================================

    public GameResponse createGame(String sessionId) {
        String gameId = gameIdGenerator.generateGameId();
        GameRoom gameRoom = new GameRoom(gameId, DEFAULT_MAX_PLAYERS, broadcaster);

        Player creator = new HumanPlayer("player1", "Player 1", PLAYER1_COLORS);
        gameRoom.addPlayer(sessionId, creator);

        gameRooms.put(gameId, gameRoom);
        sessionMapper.addSessionToGame(sessionId, gameId);

        return GameResponse.success("GAME_CREATED",
                "Game " + gameId + " created. Waiting for players (1/" + DEFAULT_MAX_PLAYERS + ")");
    }

    public GameResponse joinGame(String sessionId, String gameId) {
        GameRoom gameRoom = gameRooms.get(gameId);

        if (gameRoom == null) {
            return GameResponse.error("GAME_NOT_FOUND", "Game " + gameId + " not found");
        }

        if (!gameRoom.canJoin()) {
            return GameResponse.error("GAME_FULL", "Game " + gameId + " is full or already started");
        }

        List<PlayerColor> playerColors = gameRoom.getSessionIds().size() == 0 ? PLAYER1_COLORS : PLAYER2_COLORS;
        int playerNumber = gameRoom.getSessionIds().size() + 1;
        Player player = new HumanPlayer("player" + playerNumber, "Player " + playerNumber, playerColors);

        gameRoom.addPlayer(sessionId, player);
        sessionMapper.addSessionToGame(sessionId, gameId);

        int currentPlayers = gameRoom.getSessionIds().size();
        String message = "Joined game " + gameId + ". Waiting for players (" + currentPlayers + "/" + DEFAULT_MAX_PLAYERS + ")";

        if (gameRoom.isReady()) {
            gameRoom.startGame();
            message = "All players joined! Game starting...";
        }

        return GameResponse.success("JOINED_GAME", message);
    }

    public GameResponse leaveGame(String sessionId) {
        String gameId = sessionMapper.getGameId(sessionId);

        if (gameId == null) {
            return GameResponse.error("NO_GAME", "You are not in any game");
        }

        GameRoom gameRoom = gameRooms.get(gameId);
        if (gameRoom != null) {
            gameRoom.removePlayer(sessionId);

            if (gameRoom.getSessionIds().isEmpty()) {
                gameRooms.remove(gameId);
            }
        }

        sessionMapper.removeSession(sessionId);
        return GameResponse.success("LEFT_GAME", "Left game " + gameId);
    }

    // =============================================================================
    // GAME ACTIONS
    // =============================================================================

    public GameResponse handleDiceRoll(String sessionId) {
        GameRoom gameRoom = getGameRoomBySession(sessionId);

        if (gameRoom == null || gameRoom.getGame() == null) {
            return GameResponse.error("NO_GAME", "No active game found");
        }

        Game game = gameRoom.getGame();
        Player currentPlayer = game.getCurrentPlayer();
        Player requestingPlayer = gameRoom.getPlayer(sessionId);

        if (currentPlayer == null) {
            return GameResponse.error("GAME_NOT_READY", "Game not ready for dice roll");
        }

        if (!currentPlayer.equals(requestingPlayer)) {
            return GameResponse.error("NOT_YOUR_TURN", "It's " + currentPlayer.getPlayerName() + "'s turn");
        }

        if (game.getInputProvider() instanceof MultiplayerInputProvider) {
            MultiplayerInputProvider inputProvider = (MultiplayerInputProvider) game.getInputProvider();
            if (inputProvider.hasPendingRequest(sessionId)) {
                return GameResponse.error("PENDING_CHOICE",
                        "You must make a choice before rolling again! Available choices shown above.");
            }
        }

        try {
            game.getDice().roll();
            int die1 = game.getDice().getDie1();
            int die2 = game.getDice().getDie2();
            String playerName = requestingPlayer.getPlayerName();

            GameState gameState = game.getGameState();
            broadcaster.broadcastToGame(gameRoom.getGameId(),
                    GameResponse.success("DICE_ROLLED",
                            playerName + " rolled " + die1 + " and " + die2, gameState));

            CompletableFuture.runAsync(() -> {
                try {
                    game.continueAfterDiceRoll();
                } catch (Exception e) {
                    System.err.println("Error continuing game after dice roll: " + e.getMessage());
                }
            });

            return GameResponse.success("DICE_ROLL_RECEIVED", "Processing dice roll...");

        } catch (Exception e) {
            return GameResponse.error("DICE_ROLL_ERROR", "Error rolling dice: " + e.getMessage());
        }
    }

    public GameResponse handlePlayerChoice(String sessionId, int choice) {
        try {
            GameRoom gameRoom = getGameRoomBySession(sessionId);
            if (gameRoom == null || gameRoom.getGame() == null) {
                return GameResponse.error("NO_GAME", "No active game found");
            }

            Game game = gameRoom.getGame();
            if (game.getInputProvider() instanceof MultiplayerInputProvider) {
                MultiplayerInputProvider inputProvider = (MultiplayerInputProvider) game.getInputProvider();

                if (!inputProvider.hasPendingRequest(sessionId)) {
                    return GameResponse.error("NO_PENDING_CHOICE", "No choice currently required from you");
                }

                inputProvider.handlePlayerChoice(sessionId, choice);

                GameState updatedGameState = game.getGameState();

                broadcaster.broadcastToGame(gameRoom.getGameId(),
                        GameResponse.success("GAME_STATE_UPDATE", "Move executed", updatedGameState));

                return GameResponse.success("CHOICE_RECEIVED", "Choice processed", updatedGameState);
            }

            return GameResponse.error("INVALID_GAME", "Not a multiplayer game");

        } catch (Exception e) {
            System.err.println("Error in handlePlayerChoice: " + e.getMessage());
            return GameResponse.error("INVALID_CHOICE", "Error processing choice: " + e.getMessage());
        }
    }

    public GameResponse getGameState(String sessionId) {
        GameRoom gameRoom = getGameRoomBySession(sessionId);

        if (gameRoom == null || gameRoom.getGame() == null) {
            return GameResponse.error("NO_GAME", "No active game found");
        }

        GameState gameState = gameRoom.getGame().getGameState();
        return GameResponse.success("GAME_STATE", "Current game state", gameState);
    }

    // =============================================================================
    // HELPER METHODS
    // =============================================================================

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
        return gameRoom.getStatus() == GameRoom.GameRoomStatus.IN_PROGRESS;
    }
}