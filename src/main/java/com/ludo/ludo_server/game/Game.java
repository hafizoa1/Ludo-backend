package com.ludo.ludo_server.game;


import com.ludo.ludo_server.board.Board;
import com.ludo.ludo_server.dice.Dice;
import com.ludo.ludo_server.game.connection.StompGameEventBroadcaster;
import com.ludo.ludo_server.game.input.InputProvider;
import com.ludo.ludo_server.game.input.MultiplayerInputProvider;
import com.ludo.ludo_server.game.input.PlayerTimeoutException;
import com.ludo.ludo_server.game.state.GameState;
import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import com.ludo.ludo_server.piece.MoveOption;
import com.ludo.ludo_server.piece.Piece;
import com.ludo.ludo_server.player.Player;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.ludo.ludo_server.game.websocket.controller.ResponseType.*;

@Data
public class Game {
    private static final Logger logger = LoggerFactory.getLogger(Game.class);

    private Board board;
    private List<Player> players;
    private Dice dice;
    private InputProvider inputProvider;
    private int currentPlayerIndex;
    private Player currentPlayer;
    private String winner;
    private StompGameEventBroadcaster broadcaster;
    private String gameId;
    private MoveExecutor moveExecutor;
    // Whatever choices (moves or capture targets) were most recently offered
    // to the current player - included in GameState snapshots so clients get
    // structured data instead of having to parse the human-readable message.
    private List<MoveOption> currentOptions = new ArrayList<>();

    public Game(List<Player> players, InputProvider inputProvider,
                StompGameEventBroadcaster broadcaster, String gameId) {
        this.board = new Board(players);
        this.dice = new Dice();
        this.currentPlayerIndex = 0;
        this.players = players;
        this.inputProvider = inputProvider;
        this.currentPlayer = players.get(currentPlayerIndex);
        this.broadcaster = broadcaster;  // ADD THIS
        this.gameId = gameId;
        this.moveExecutor = new MoveExecutor(this.board, this.inputProvider,
                options -> this.currentOptions = options);
    }

    public GameState getGameState() {
        return new GameState(this);  // That's it! Your GameState constructor does all the work
    }

    public boolean isGameOver() {
        for (Player player : players) {
            if (player.hasWon()) {
                winner = player.getPlayerName();
                inputProvider.sendMessage("🎉 " + winner + " has won the game!");
                return true;
            }
        }
        return false;
    }

    public void startGame() {
        board.printBoard();
        board.printLegend();

        // Initialize first turn but don't auto-play
        this.currentPlayer = players.get(currentPlayerIndex);

// Set current player for input provider
        if (inputProvider instanceof MultiplayerInputProvider) {
            MultiplayerInputProvider multiInput = (MultiplayerInputProvider) inputProvider;
            multiInput.setCurrentPlayer(this.currentPlayer);
            multiInput.sendTurnNotification("It's your turn. Send ROLL_DICE to roll the dice.");
        }

// Just ask for dice roll - don't roll automatically
        inputProvider.sendMessage("It's " + this.currentPlayer.getPlayerName() + "'s turn. Send ROLL_DICE to roll the dice.");

// Game now waits for ROLL_DICE WebSocket message
    }

    public void continueAfterDiceRoll() {
        try {
            logger.debug("Continuing after dice roll for {}", currentPlayer.getPlayerName());

            // Announce dice result
            //inputProvider.sendMessage(currentPlayer.getPlayerName() + " rolled " + dice.getDie1() + " and " + dice.getDie2());

            // Execute the rest of the turn (show moves, wait for choice, etc.)
            executePlayerTurn(currentPlayer);

            // Check if game is over after the turn
            if (isGameOver()) {
                logger.info("Game over! Winner: {}", winner);
                return; // Game ends here
            }

            // Move to next player (unless they rolled double 6)
            if (!dice.isDoubleSix()) {
                int oldPlayerIndex = currentPlayerIndex;
                currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
                this.currentPlayer = players.get(currentPlayerIndex);
                logger.debug("Switched from player {} to player {}", oldPlayerIndex, currentPlayerIndex);
            } else {
                inputProvider.sendMessage(currentPlayer.getPlayerName() + " rolled a Double 6! Goes again!");
                logger.debug("Double 6! {} plays again", currentPlayer.getPlayerName());
            }

            // Validate current player index
            if (currentPlayerIndex < 0 || currentPlayerIndex >= players.size()) {
                logger.warn("Invalid currentPlayerIndex: {} for {} players", currentPlayerIndex, players.size());
                currentPlayerIndex = 0; // Reset to first player as fallback
                this.currentPlayer = players.get(currentPlayerIndex);
            }

            // Set up next turn
            if (inputProvider instanceof MultiplayerInputProvider) {
                MultiplayerInputProvider multiInput = (MultiplayerInputProvider) inputProvider;
                multiInput.setCurrentPlayer(this.currentPlayer);
                multiInput.sendTurnNotification("It's your turn. Send ROLL_DICE to roll the dice.");
            }

            inputProvider.sendMessage("It's " + this.currentPlayer.getPlayerName() + "'s turn. Send ROLL_DICE to roll the dice.");
            logger.debug("Set up next turn for {}", this.currentPlayer.getPlayerName());

            board.printBoard(); // Show updated board

        } catch (PlayerTimeoutException e) {
            // Let GameManager's handlePlayerTimeout end the game and declare the
            // other player winner - don't swallow it in the generic catch below.
            throw e;
        } catch (Exception e) {
            logger.error("Error in continueAfterDiceRoll: {}", e.getMessage(), e);

            // Try to recover by resetting to a valid state
            if (currentPlayerIndex < 0 || currentPlayerIndex >= players.size()) {
                currentPlayerIndex = 0;
                this.currentPlayer = players.get(currentPlayerIndex);
            }

            inputProvider.sendMessage("An error occurred. Continuing with " + this.currentPlayer.getPlayerName() + "'s turn.");
        }
    }

    private void executePlayerTurn(Player currentPlayer) {
        // Set current player for input provider
        if (inputProvider instanceof MultiplayerInputProvider) {
            MultiplayerInputProvider multiInput = (MultiplayerInputProvider) inputProvider;
            multiInput.setCurrentPlayer(currentPlayer);
        }

        // Initialize available dice values at start of turn
        List<Integer> availableDiceValues = new ArrayList<>();
        availableDiceValues.add(dice.getDie1());
        availableDiceValues.add(dice.getDie2());

        logger.debug("Starting turn for {}", currentPlayer.getPlayerName());
        logger.debug("Dice rolled: {} and {}", dice.getDie1(), dice.getDie2());
        logger.debug("Available dice values: {}", availableDiceValues);

        // Continue until no more moves possible or player chooses to stop
        while (!availableDiceValues.isEmpty()) {
            logger.debug("Remaining dice values: {}", availableDiceValues);

            List<MoveOption> availableMoves = moveExecutor.calculatePossibleMoves(currentPlayer, availableDiceValues);

            logger.debug("Found {} possible moves", availableMoves.size());

            if (availableMoves.isEmpty()) {
                currentOptions = new ArrayList<>();
                inputProvider.sendMessage("No more viable moves. Turn ends.");
                logger.debug("No moves available, ending turn");
                break;
            }

            currentOptions = availableMoves;

            // Display moves as one batched message
             displayAvailableMoves(availableMoves);

            // Get player choice with proper validation
            int choice = inputProvider.getChoice(1, availableMoves.size(),
                    "Enter game option (1-" + availableMoves.size() + ")");

            logger.debug("Player chose option {}", choice);

            // Validate choice index (extra safety check)
            if (choice < 1 || choice > availableMoves.size()) {
                logger.warn("Choice {} is out of bounds for {} moves", choice, availableMoves.size());
                inputProvider.sendMessage("Invalid choice! Please try again.");
                continue; // Skip this iteration, ask again
            }

            // Execute the chosen move
            MoveOption selectedMove = availableMoves.get(choice - 1); // Convert to 0-based index
            logger.debug("Selected move: {}", selectedMove.generateDescription());
            logger.debug("Using dice value: {}", selectedMove.getDiceValue());

            executeMove(selectedMove);
            currentOptions = new ArrayList<>(); // the offered choice has now been resolved

            if (broadcaster != null && gameId != null) {
                GameState currentState = getGameState();
                broadcaster.broadcastToGame(gameId,
                        GameResponse.success(GAME_STATE_UPDATE, "Move executed", currentState));
            }


            // Remove the used dice value
            boolean removed = availableDiceValues.remove(Integer.valueOf(selectedMove.getDiceValue()));
            logger.debug("Removed dice value {} from available dice. Success: {}", selectedMove.getDiceValue(), removed);

            inputProvider.sendMessage("Used die value: " + selectedMove.getDiceValue());
            inputProvider.sendMessage("Remaining dice: " + availableDiceValues);
        }

        logger.debug("Turn completed for {}", currentPlayer.getPlayerName());
    }


    private void executeMove(MoveOption move) {
        Piece piece = move.getPiece();
        int diceValue = move.getDiceValue();
        Player movingPlayer = move.getPlayer();

        moveExecutor.movePiece(piece, movingPlayer, diceValue);
    }

    private void displayAvailableMoves(List<MoveOption> moves) {
        StringBuilder movesList = new StringBuilder("Available moves:\n");
        for (int i = 0; i < moves.size(); i++) {
            movesList.append((i + 1)).append(". ").append(moves.get(i).generateDescription()).append("\n");
        }

        // Send to current player's personal queue only (not broadcast)
        if (inputProvider instanceof MultiplayerInputProvider) {
            MultiplayerInputProvider multiInput = (MultiplayerInputProvider) inputProvider;
            multiInput.sendMoveOptions(movesList.toString(), moves.size());
            logger.debug("{}", movesList);
        } else {
            // Fallback for non-multiplayer
            inputProvider.sendMessage(movesList.toString());
        }
    }


}