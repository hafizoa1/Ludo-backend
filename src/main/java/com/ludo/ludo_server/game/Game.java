package com.ludo.ludo_server.game;


import com.ludo.ludo_server.board.Board;
import com.ludo.ludo_server.board.Position;
import com.ludo.ludo_server.dice.Dice;
import com.ludo.ludo_server.game.connection.StompGameEventBroadcaster;
import com.ludo.ludo_server.game.input.InputProvider;
import com.ludo.ludo_server.game.input.MultiplayerInputProvider;
import com.ludo.ludo_server.game.state.GameState;
import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import com.ludo.ludo_server.piece.MoveOption;
import com.ludo.ludo_server.piece.MoveType;
import com.ludo.ludo_server.piece.Piece;
import com.ludo.ludo_server.player.Player;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

import static com.ludo.ludo_server.game.websocket.controller.ResponseType.*;

@Data
public class Game {
    private Board board;
    private List<Player> players;
    private Dice dice;
    private InputProvider inputProvider;
    private int currentPlayerIndex;
    private Player currentPlayer;
    private String winner;
    private StompGameEventBroadcaster broadcaster;
    private String gameId;

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
            System.out.println("DEBUG: Continuing after dice roll for " + currentPlayer.getPlayerName());

            // Announce dice result
            //inputProvider.sendMessage(currentPlayer.getPlayerName() + " rolled " + dice.getDie1() + " and " + dice.getDie2());

            // Execute the rest of the turn (show moves, wait for choice, etc.)
            executePlayerTurn(currentPlayer);

            // Check if game is over after the turn
            if (isGameOver()) {
                inputProvider.sendMessage("🎉 " + winner + " has won the game!");
                System.out.println("DEBUG: Game over! Winner: " + winner);
                return; // Game ends here
            }

            // Move to next player (unless they rolled double 6)
            if (!dice.isDoubleSix()) {
                int oldPlayerIndex = currentPlayerIndex;
                currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
                this.currentPlayer = players.get(currentPlayerIndex);
                System.out.println("DEBUG: Switched from player " + oldPlayerIndex + " to player " + currentPlayerIndex);
            } else {
                inputProvider.sendMessage(currentPlayer.getPlayerName() + " rolled a Double 6! Goes again!");
                System.out.println("DEBUG: Double 6! " + currentPlayer.getPlayerName() + " plays again");
            }

            // Validate current player index
            if (currentPlayerIndex < 0 || currentPlayerIndex >= players.size()) {
                System.err.println("ERROR: Invalid currentPlayerIndex: " + currentPlayerIndex + " for " + players.size() + " players");
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
            System.out.println("DEBUG: Set up next turn for " + this.currentPlayer.getPlayerName());

            board.printBoard(); // Show updated board

        } catch (Exception e) {
            System.err.println("ERROR in continueAfterDiceRoll: " + e.getMessage());
            e.printStackTrace();

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

        System.out.println("DEBUG: Starting turn for " + currentPlayer.getPlayerName());
        System.out.println("DEBUG: Dice rolled: " + dice.getDie1() + " and " + dice.getDie2());
        System.out.println("DEBUG: Available dice values: " + availableDiceValues);

        // Continue until no more moves possible or player chooses to stop
        while (!availableDiceValues.isEmpty()) {
            System.out.println("DEBUG: Remaining dice values: " + availableDiceValues);

            List<MoveOption> availableMoves = calculatePossibleMoves(currentPlayer, availableDiceValues);

            System.out.println("DEBUG: Found " + availableMoves.size() + " possible moves");

            if (availableMoves.isEmpty()) {
                inputProvider.sendMessage("No more viable moves. Turn ends.");
                System.out.println("DEBUG: No moves available, ending turn");
                break;
            }

            // Display moves as one batched message
             displayAvailableMoves(availableMoves);

            // Get player choice with proper validation
            int choice = inputProvider.getChoice(1, availableMoves.size(),
                    "Enter game option (1-" + availableMoves.size() + ")");

            System.out.println("DEBUG: Player chose option " + choice);

            // Validate choice index (extra safety check)
            if (choice < 1 || choice > availableMoves.size()) {
                System.err.println("ERROR: Choice " + choice + " is out of bounds for " + availableMoves.size() + " moves");
                inputProvider.sendMessage("Invalid choice! Please try again.");
                continue; // Skip this iteration, ask again
            }

            // Execute the chosen move
            MoveOption selectedMove = availableMoves.get(choice - 1); // Convert to 0-based index
            System.out.println("DEBUG: Selected move: " + selectedMove.generateDescription());
            System.out.println("DEBUG: Using dice value: " + selectedMove.getDiceValue());

            executeMove(selectedMove);

            if (broadcaster != null && gameId != null) {
                GameState currentState = getGameState();
                broadcaster.broadcastToGame(gameId,
                        GameResponse.success(GAME_STATE_UPDATE, "Move executed", currentState));
            }


            // Remove the used dice value
            boolean removed = availableDiceValues.remove(Integer.valueOf(selectedMove.getDiceValue()));
            System.out.println("DEBUG: Removed dice value " + selectedMove.getDiceValue() + " from available dice. Success: " + removed);

            inputProvider.sendMessage("Used die value: " + selectedMove.getDiceValue());
            inputProvider.sendMessage("Remaining dice: " + availableDiceValues);
        }

        System.out.println("DEBUG: Turn completed for " + currentPlayer.getPlayerName());
    }


    private void executeMove(MoveOption move) {
        Piece piece = move.getPiece();
        int diceValue = move.getDiceValue();
        Player movingPlayer = move.getPlayer();

        // Use Game's movePiece method (handles captures)
        movePiece(piece, movingPlayer,  diceValue);

        inputProvider.sendMessage("Used die value: " + diceValue);
    }


    private List<MoveOption> calculatePossibleMoves(Player currentPlayer, List<Integer> availableDice) {
        List<MoveOption> moveOptions = new ArrayList<>();

        // Handle bringing pieces out of home (if 6 is available)
        if (availableDice.contains(6)) {
            List<Piece> homePieces = currentPlayer.getHomePieces();
            for (Piece piece : homePieces) {
                moveOptions.add(new MoveOption(currentPlayer, piece, 6, MoveType.BRING_OUT));
            }
        }

        // Handle moving active pieces with available dice
        List<Piece> activePieces = currentPlayer.getActivePieces();
        for (Piece piece : activePieces) {
            for (Integer diceValue : availableDice) {
                if (piece.canMove(diceValue)) {
                    moveOptions.add(new MoveOption(currentPlayer, piece, diceValue, MoveType.NORMAL));
                }
            }
        }

        return moveOptions;
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
            System.out.println(movesList);
        } else {
            // Fallback for non-multiplayer
            inputProvider.sendMessage(movesList.toString());
        }
    }


    /**
     * Move a piece and handle all game logic (movement, captures, etc.)
     */
    public Position movePiece(Piece piece, Player movingPlayer, int diceValue) {
        if (piece == null) {
            System.err.println("Cannot move a null piece.");
            return null;
        }

        Position oldPosition = piece.getBoardPosition();
        Position newPosition = piece.move(diceValue); // Piece calculates its new position

        // If piece actually moved to a new position
        if (!oldPosition.equals(newPosition)) {

            // Remove from old position
            board.removePiece(oldPosition.row(), oldPosition.col()); /// WHY DONT WE SAY IF PIECE.NEWPOSITION

            // Update board position tracking
            if (piece.isFinished()) {
                // Place at center
                board.placePiece(piece, 7, 7);
            } else {
                // Normal placement
                board.placePiece(piece, newPosition.row(), newPosition.col());

                // Check for captures at the new position
                checkForCaptures(movingPlayer, piece, newPosition);
            }
            return newPosition;
        } else {
            return oldPosition; // Piece didn't move
        }
    }

    /**
     * Check if the moved piece can capture any pieces at its new position
     */
    private void checkForCaptures(Player player, Piece movingPiece, Position position) {
        List<Piece> piecesAtPosition = board.getPiecesAt(position.row(), position.col());

        // Early exit if no other pieces at this position
        if (piecesAtPosition.isEmpty() || piecesAtPosition.size() == 1) {
            return; // No pieces to capture (empty or just the moving piece)
        }

        // Find all pieces that can be captured
        List<Piece> capturablePieces = new ArrayList<>();
        for (Piece otherPiece : piecesAtPosition) {
            if (movingPiece.canCapture(player, otherPiece)) {
                capturablePieces.add(otherPiece);
            }
        }

        // Early exit if no captures possible
        if (capturablePieces.isEmpty()) {
            return;
        }

        // Handle captures based on count
        if (capturablePieces.size() == 1) {
            // Only one piece to capture - do it automatically
            capturePiece(movingPiece, capturablePieces.get(0));
        } else {
            // Multiple pieces can be captured - let player choose
            choosePieceToCapture(movingPiece, capturablePieces);
        }
    }


    private void choosePieceToCapture(Piece capturingPiece, List<Piece> capturablePieces) {
        inputProvider.sendMessage(capturingPiece.getId() + " can capture multiple pieces:");

        // Display options
        for (int i = 0; i < capturablePieces.size(); i++) {
            Piece piece = capturablePieces.get(i);
            inputProvider.sendMessage((i + 1) + ". Capture " + piece.getId() + " (" + piece.getColor() + ")");
        }

        // Get player choice
        int choice = inputProvider.getChoice(1, capturablePieces.size(),
                "Choose which piece to capture (1-" + capturablePieces.size() + "):");


        // Execute the capture (no choice to skip)
        Piece chosenPiece = capturablePieces.get(choice - 1);
        capturePiece(capturingPiece, chosenPiece);
    }

    /**
     * Execute a capture - send captured piece home
     */
    private void capturePiece(Piece capturingPiece, Piece capturedPiece) {
        // Guard clause - no captures involving finished pieces
        if (capturingPiece.isFinished() || capturedPiece.isFinished()) {
            return; // No capture possible
        }

        // Remove captured piece from current position
        board.removePiece(capturedPiece);

        // Remove capturing piece from current position
        board.removePiece(capturingPiece);

        // Send captured piece back to its home
        sendPieceHome(capturedPiece);

        // Send capturing piece to finished position (your custom rule)
        sendPieceToFinished(capturingPiece);

        inputProvider.sendMessage("💥 " + capturingPiece.getId() + " captured " + capturedPiece.getId() + "!");
        inputProvider.sendMessage(capturedPiece.getId() + " sent home, " + capturingPiece.getId() + " finished the game!");
    }

    private void sendPieceToFinished(Piece piece) {
        // Set to finished position and update board position
        piece.setPathPosition(Piece.FINISHED_POSITION); // Use the constant instead of 56
        Position centerPosition = new Position(7, 7); // Center of board
        piece.setBoardPosition(centerPosition);

        // Place piece at center on board
        board.placePiece(piece, 7, 7);

        inputProvider.sendMessage(piece.getId() + " has finished the game!");
    } // captrure can oonly be done if oal die has ben played

    /**
     * Send a piece back to its home position
     */
    private void sendPieceHome(Piece piece) {
        // Get the piece's original home position
        Position homePosition = getHomePositionForPiece(piece);

        // Reset piece state
        piece.sendHome(homePosition); // Sets pathPosition = -1 and board position

        // Place piece back on board at home
        board.placePiece(piece, homePosition.row(), homePosition.col());

        inputProvider.sendMessage(piece.getId() + " was sent home to " + homePosition);
    }

    /**
     * Get the home position for a specific piece
     */
    private Position getHomePositionForPiece(Piece piece) {
        Position[] homeCoords = Board.initialHomeYardCoords.get(piece.getColor());
        // Use piece number to get correct home position (R1 = index 0, R2 = index 1, etc.)
        return homeCoords[piece.getNumber() - 1];
    }
}