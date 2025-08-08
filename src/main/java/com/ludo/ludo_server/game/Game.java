package com.ludo.ludo_server.game;


import com.ludo.ludo_server.board.Board;
import com.ludo.ludo_server.board.Position;
import com.ludo.ludo_server.dice.Dice;
import com.ludo.ludo_server.game.input.InputProvider;
import com.ludo.ludo_server.piece.MoveOption;
import com.ludo.ludo_server.piece.MoveType;
import com.ludo.ludo_server.piece.Piece;
import com.ludo.ludo_server.player.Player;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
public class Game {
    private Board board;
    private List<Player> players;
    private Dice dice;
    private InputProvider inputProvider;
    private int currentPlayerIndex;
    private Player currentPlayer;
    private String winner;

    public Game(List<Player> players, InputProvider inputProvider) {
        this.board = new Board(players);
        this.dice = new Dice();
        this.currentPlayerIndex = 0;
        this.players = players;
        this.inputProvider = inputProvider;
        this.currentPlayer = players.get(currentPlayerIndex);
        // TODO: Initialize players list
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

        while (!isGameOver()) {
            Player currentPlayer = players.get(currentPlayerIndex);
            inputProvider.sendMessage("It's " + currentPlayer.getPlayerName() + "'s turn.\n \"Press anything to roll dice\"");
            dice.roll();
            inputProvider.sendMessage(currentPlayer.getPlayerName() + " rolled " + dice.getDie1() + " and " + dice.getDie2());

            executePlayerTurn(currentPlayer);

            // Move to next player (unless they rolled a 6)
            if (!dice.isDoubleSix()) {
                currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            } else {
                inputProvider.sendMessage(currentPlayer.getPlayerName() + " rolled a Double 6! Goes again!");
            }

            board.printBoard();
        }
    }

    private void executePlayerTurn(Player currentPlayer) {
        // Initialize available dice values at start of turn
        List<Integer>availableDiceValues = new ArrayList<>();
        availableDiceValues.add(dice.getDie1());
        availableDiceValues.add(dice.getDie2());


        // Continue until no more moves possible or player chooses to stop
        while (!availableDiceValues.isEmpty()) {
            List<MoveOption> availableMoves = calculatePossibleMoves(currentPlayer, availableDiceValues);

            if (availableMoves.isEmpty()) {
                System.out.println("No more viable moves. Turn ends.");
                break;
            }

            displayAvailableMoves(availableMoves);
            System.out.println("Enter game option (1-" + availableMoves.size() + "):");

            int choice = -1;
            boolean validChoice = false;

            // Input validation loop
            inputProvider.getChoice(1, availableMoves.size(),
                    "Enter game option (1-" + availableMoves.size() + "):");

                // Execute the chosen move
            MoveOption selectedMove = availableMoves.get(choice - 1); // Convert to 0-based index
            executeMove(selectedMove);

            availableDiceValues.remove(Integer.valueOf(selectedMove.getDiceValue()));
            inputProvider.sendMessage("Remaining dice: " + availableDiceValues);
        }
            // Remove the used die value
    }

    private void executeMove(MoveOption move) {
        Piece piece = move.getPiece();
        int diceValue = move.getDiceValue();

        // Use Game's movePiece method (handles captures)
        movePiece(piece, diceValue);

        inputProvider.sendMessage("Used die value: " + diceValue);
    }


    private List<MoveOption> calculatePossibleMoves(Player currentPlayer, List<Integer> availableDice) {
        List<MoveOption> moveOptions = new ArrayList<>();

        // Handle bringing pieces out of home (if 6 is available)
        if (availableDice.contains(6)) {
            List<Piece> homePieces = currentPlayer.getHomePieces();
            for (Piece piece : homePieces) {
                moveOptions.add(new MoveOption(piece, 6, MoveType.BRING_OUT));
            }
        }

        // Handle moving active pieces with available dice
        List<Piece> activePieces = currentPlayer.getActivePieces();
        for (Piece piece : activePieces) {
            for (Integer diceValue : availableDice) {
                if (piece.canMove(diceValue)) {
                    moveOptions.add(new MoveOption(piece, diceValue, MoveType.NORMAL));
                }
            }
        }

        return moveOptions;
    }

    private void displayAvailableMoves(List<MoveOption> moves) {
        inputProvider.sendMessage("Available moves:");
        for (int i = 0; i < moves.size(); i++) {
            inputProvider.sendMessage((i + 1) + ". " + moves.get(i).generateDescription());
        }
    }


    /**
     * Move a piece and handle all game logic (movement, captures, etc.)
     */
    public Position movePiece(Piece piece, int diceValue) {
        if (piece == null) {
            System.err.println("Cannot move a null piece.");
            return null;
        }

        Position oldPosition = piece.getBoardPosition();
        Position newPosition = piece.move(diceValue); // Piece calculates its new position

        // If piece actually moved to a new position
        if (!oldPosition.equals(newPosition)) {

            // Remove from old position
            board.removePiece(oldPosition.getRow(), oldPosition.getCol());

            // Update board position tracking
            if (piece.isFinished()) {
                // Place at center
                board.placePiece(piece, 7, 7);
            } else {
                // Normal placement
                board.placePiece(piece, newPosition.getRow(), newPosition.getCol());

                // Check for captures at the new position
                checkForCaptures(piece, newPosition);
            }
            return newPosition;
        } else {
            return oldPosition; // Piece didn't move
        }
    }

    /**
     * Check if the moved piece can capture any pieces at its new position
     */
    private void checkForCaptures(Piece movingPiece, Position position) {
        List<Piece> piecesAtPosition = board.getPiecesAt(position.getRow(), position.getCol());

        // Early exit if no other pieces at this position
        if (piecesAtPosition.isEmpty() || piecesAtPosition.size() == 1) {
            return; // No pieces to capture (empty or just the moving piece)
        }

        // Find all pieces that can be captured
        List<Piece> capturablePieces = new ArrayList<>();
        for (Piece otherPiece : piecesAtPosition) {
            if (movingPiece.canCapture(otherPiece)) {
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
        // Remove captured piece from current position
        board.removePiece(capturedPiece);

        // Remove capturing piece from current position
        board.removePiece(capturingPiece);

        // Send captured piece back to its home
        sendPieceHome(capturedPiece);

        // Send capturing piece to finished position
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
        board.placePiece(piece, homePosition.getRow(), homePosition.getCol());

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