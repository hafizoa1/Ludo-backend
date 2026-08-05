package com.ludo.ludo_server.game;

import com.ludo.ludo_server.board.Board;
import com.ludo.ludo_server.board.Position;
import com.ludo.ludo_server.game.input.InputProvider;
import com.ludo.ludo_server.game.input.MultiplayerInputProvider;
import com.ludo.ludo_server.piece.MoveOption;
import com.ludo.ludo_server.piece.MoveType;
import com.ludo.ludo_server.piece.Piece;
import com.ludo.ludo_server.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Resolves what moves a player can make and what happens when a move is
 * executed - including captures and this ruleset's custom rule that a
 * capture instantly finishes the capturing piece. Pulled out of Game so
 * that "what's possible / what happens to the board" is separate from
 * whose turn it is and how a turn is sequenced.
 */
public class MoveExecutor {

    private static final Logger logger = LoggerFactory.getLogger(MoveExecutor.class);

    private final Board board;
    private final InputProvider inputProvider;
    // Reports back whatever choices (moves or capture targets) were just
    // offered, so Game can include them in the next GameState snapshot -
    // MoveExecutor doesn't hold a reference to Game itself, this is the seam.
    private final Consumer<List<MoveOption>> onOptionsOffered;

    public MoveExecutor(Board board, InputProvider inputProvider, Consumer<List<MoveOption>> onOptionsOffered) {
        this.board = board;
        this.inputProvider = inputProvider;
        this.onOptionsOffered = onOptionsOffered;
    }

    public List<MoveOption> calculatePossibleMoves(Player currentPlayer, List<Integer> availableDice) {
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

    /**
     * Move a piece and handle all game logic (movement, captures, etc.)
     */
    public Position movePiece(Piece piece, Player movingPlayer, int diceValue) {
        if (piece == null) {
            logger.warn("Cannot move a null piece.");
            return null;
        }

        Position oldPosition = piece.getBoardPosition();
        Position newPosition = piece.move(diceValue); // Piece calculates its new position

        // If piece actually moved to a new position
        if (!oldPosition.equals(newPosition)) {

            // Remove from old position
            board.removePiece(oldPosition.row(), oldPosition.col());

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
            choosePieceToCapture(player, movingPiece, capturablePieces);
        }
    }

    private void choosePieceToCapture(Player player, Piece capturingPiece, List<Piece> capturablePieces) {
        // Build the capture options message
        StringBuilder captureOptions = new StringBuilder();
        captureOptions.append(capturingPiece.getId()).append(" can capture multiple pieces:\n");

        for (int i = 0; i < capturablePieces.size(); i++) {
            Piece piece = capturablePieces.get(i);
            captureOptions.append((i + 1)).append(". Capture ")
                         .append(piece.getId()).append(" (")
                         .append(piece.getColor()).append(")\n");
        }

        // Report what's being offered (as MoveOptions, same shape as a normal
        // move choice) before sending the message, so the snapshot attached
        // to that message already reflects these as the current options.
        List<MoveOption> captureChoices = new ArrayList<>();
        for (Piece capturable : capturablePieces) {
            captureChoices.add(new MoveOption(player, capturable, 0, MoveType.CAPTURE));
        }
        onOptionsOffered.accept(captureChoices);

        // Send capture options using the proper message type
        if (inputProvider instanceof MultiplayerInputProvider) {
            MultiplayerInputProvider multiInput = (MultiplayerInputProvider) inputProvider;
            multiInput.sendCaptureOptions(captureOptions.toString(), capturablePieces.size());
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

        // Send capturing piece to finished position (this ruleset's custom rule)
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
    }

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
