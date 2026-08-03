package com.ludo.ludo_server.piece;



import com.ludo.ludo_server.board.Position;
import com.ludo.ludo_server.player.Player;
import com.ludo.ludo_server.player.PlayerColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;

/**
 * Piece class - Represents a game piece in Ludo.
 * Each piece has a color, number, ID, and tracks its position on its specific path.
 * It interacts with the static MovementPath class to get its board coordinates.
 */
public class Piece {

    private static final Logger logger = LoggerFactory.getLogger(Piece.class);

    private final String id;           // "R1", "G2", etc.
    private final PlayerColor color;   // RED, GREEN, BLUE, YELLOW enum
    private final int number;          // 1, 2, 3, or 4 (to generate ID)

    private Position currentBoardPosition; // Current (row, col) on board
    private int pathPosition;    // Position on movement path (-1 = home, 0 to 55-ish = on path, 56 = finished)
    public static final int MAX_PATH_POSITION = 55; // Path positions 0-55.
    public static final int FINISHED_POSITION = MAX_PATH_POSITION + 1; // Represents the state of being finished (56)

    /**
     * Constructor for creating a piece.
     * Initializes the piece's properties and sets its initial state to 'at home'.
     *
     * @param color piece color (PlayerColor enum)
     * @param pieceNumber piece number (1, 2, 3, 4) used for ID generation
     */
    public Piece(PlayerColor color, int pieceNumber) {
        this.color = color;
        this.number = pieceNumber;
        this.id = color.getShortName().charAt(0) + String.valueOf(pieceNumber); // e.g., "R1"
        this.pathPosition = -1;  // Start at home
        this.currentBoardPosition = MovementPath.getCoordinateAt(pieceNumber, this.color);

    }

    // ========== MOVEMENT METHODS ==========




    /**
     * Attempts to move this piece by the given dice value.
     * Updates the piece's path position and board coordinates if the move is valid.
     *
     * @param diceValue The value rolled on the dice (number of steps to move).
     * @return The new Board.Position after the move, or the current position if the move is invalid.
     */
    public Position move(int diceValue) {
        // --- Core Ludo Game Rules for Movement ---
        // 1. Must roll a 6 to exit home.
        if (isAtHome()) {
            if (diceValue == 6) {
                this.pathPosition = 0; // Move to the entry point (path position 0)
                updateBoardPosition(); // Update physical coordinates
                logger.debug("{} moved to path position {} at board coordinates {}", this.id, pathPosition, currentBoardPosition);
                return currentBoardPosition;
            } else {
                logger.debug("{} needs a 6 to exit home (rolled {}). Stays at home.", this.id, diceValue);
                return currentBoardPosition; // Piece stays at home
            }
        }

        // 2. Cannot move if already finished.
        if (isFinished()) {
            logger.debug("{} is already finished and cannot move.", this.id);
            return currentBoardPosition;
        }

        // 3. Calculate potential new position.
        int potentialNewPathPosition = this.pathPosition + diceValue;

        // 4. Cannot overshoot the finish line.
        if (potentialNewPathPosition > FINISHED_POSITION) {
            logger.debug("{} cannot move {} steps (overshot finish line). Stays at {}", this.id, diceValue, this.pathPosition);
            return currentBoardPosition; // Piece stays at current position
        }

        // 5. If it lands exactly on FINISHED_POSITION, they finish!
        if (potentialNewPathPosition == FINISHED_POSITION) {
            this.pathPosition = FINISHED_POSITION; // Set to 57
            updateBoardPosition(); // This should handle FINISHED_POSITION and place at [7,7]
            logger.debug("{} can finish game!", this.id);
            return currentBoardPosition;
        }

        // If all checks pass, update the path position and board coordinates normally
        this.pathPosition = potentialNewPathPosition;
        updateBoardPosition();

        logger.debug("{} moved to path position {} at board coordinates {}", this.id, pathPosition, currentBoardPosition);

        return currentBoardPosition;
    }

    public Position calculateNewPosition(int diceValue) {
        int potentialNewPathPosition = this.pathPosition + diceValue;
        return MovementPath.getCoordinateAt(this.color, potentialNewPathPosition);
    }

    /**
     * Updates the piece's current board (row, col) coordinates based on its
     * current `pathPosition`. This method queries the `MovementPath` utility.
     * Only works for `pathPosition` >= 0. For home position (-1),
     * `setBoardPosition` must be used explicitly.
     */
    public void updateBoardPosition() {
        if (this.pathPosition == FINISHED_POSITION) {
            this.currentBoardPosition = new Position(7, 7); // Piece at finish center
        } else if (this.pathPosition >= 0) { // Only query MovementPath for valid path positions (0-56)
            Position newBoardPos = MovementPath.getCoordinateAt(this.color, this.pathPosition);
            if (newBoardPos != null) {
                this.currentBoardPosition = newBoardPos;
            } else {
                logger.warn("Could not determine board position for {} at pathPosition {} (returned null from MovementPath).",
                        this.id, this.pathPosition);
            }
        }
        // If pathPosition is -1 (home), currentBoardPosition should already be set
    }

    /**
     * Get the current board position (row, col) of the piece.
     * @return A Board.Position object representing the piece's current location.
     */
    public Position getBoardPosition() {
        return currentBoardPosition;
    }

    /**
     * Set the piece's current board position.
     * Used internally by Board.placePiece or for initial setup.
     * This method does NOT update the pathPosition, only the visual coordinate.
     * @param position The new Board.Position.
     */
    public void setBoardPosition(Position position) {
        this.currentBoardPosition = position;
    }

    // ========== GAME RULE CHECKS ==========

    /**
     * Check if this piece can capture another piece at the given position.
     * Capturing rules: Must be different colors, neither piece is at home or finished,
     * and both are at the exact same board position.
     *
     * @param otherPiece The piece to potentially capture.
     * @return true if this piece can capture the other piece, false otherwise.
     */
    public boolean canCapture(Player player, Piece otherPiece) {


        // A piece cannot capture itself or a piece of the same color.
        if (this.equals(otherPiece) || this.color == otherPiece.color) {
            return false;
        }

        //A piece cnnot capture another if they have the same player
        if (Arrays.asList(player.getPieces()).contains(otherPiece)) {
            return false;
        }

        // Pieces at home or finished cannot be captured or capture.
        if (this.isAtHome() || this.isFinished() ||
                otherPiece.isAtHome() || otherPiece.isFinished()) { // Pieces in safe zones cannot be captured
            return false;
        }

        // Can capture if both pieces are at the same board coordinates.
        return this.currentBoardPosition.equals(otherPiece.currentBoardPosition);
    }

    /**
     * Check if piece can move with given dice value.
     * This method contains core Ludo movement rules.
     * (Most of the rule logic is now integrated into the `move` method,
     * but this method can be used for pre-checks or AI strategy).
     *
     * @param diceValue The dice roll value.
     * @return true if the piece can make this move according to Ludo rules, false otherwise.
     */
    public boolean canMove(int diceValue) {
        // Rule: Must roll a 6 to exit home.
        if (isAtHome()) {
            return diceValue == 6;
        }

        // Rule: Cannot move if already finished.
        if (isFinished()) {
            return false;
        }

        int potentialNewPathPosition = this.pathPosition + diceValue;
        // Fix: Check against FINISHED_POSITION, not MAX_PATH_POSITION
        return potentialNewPathPosition <= FINISHED_POSITION;
    }

    // ========== GETTERS AND SETTERS ==========

    public String getId() {
        return id;
    }

    public PlayerColor getColor() {
        return color;
    }

    public int getNumber() {
        return number;
    }


    /**
     * Get the piece's position on its movement path.
     * @return pathPosition (-1 = home, 0 to MAX_PATH_POSITION = on path, FINISHED_POSITION = finished)
     */
    public int getPathPosition() {
        return pathPosition;
    }

    /**
     * Set the piece's position on its movement path.
     * This method should be used carefully, as it directly manipulates the piece's path state.
     * It also automatically updates the board coordinates to match the new path position.
     * @param pathPosition new path position
     */
    public void setPathPosition(int pathPosition) {
        this.pathPosition = pathPosition;
        updateBoardPosition(); // Update board coordinates whenever path position changes
    }

    // ========== STATE CHECK METHODS ==========

    /**
     * Check if piece is at its home base (before starting the main path).
     * @return true if piece is in starting position (`pathPosition == -1`).
     */
    public boolean isAtHome() {
        return pathPosition == -1;
    }

    /**
     * Check if piece has finished the game (reached the center).
     * @return true if piece has reached the end (`pathPosition == FINISHED_POSITION`).
     */
    public boolean isFinished() {
        return pathPosition == FINISHED_POSITION;
    }


    // ========== DISPLAY METHODS ==========


    /**
     * Get colored display string with ANSI colors for terminal output.
     * @return colored piece display string with ANSI escape codes.
     */
    public String getColoredDisplay() {
        final String RED_COLOR = "\u001B[91m";      // Bright red
        final String GREEN_COLOR = "\u001B[92m";    // Bright green
        final String BLUE_COLOR = "\u001B[94m";     // Bright blue
        final String YELLOW_COLOR = "\u001B[93m";   // Bright yellow
        final String RESET = "\u001B[0m";     // Reset to default color

        String displayText = String.format("%s%d", color.getShortName().charAt(0), number); // e.g., "R1"

        switch (color) {
            case RED:
                return RED_COLOR + displayText + RESET;
            case GREEN:
                return GREEN_COLOR + displayText + RESET;
            case BLUE:
                return BLUE_COLOR + displayText + RESET;
            case YELLOW:
                return YELLOW_COLOR + displayText + RESET;
            default:
                return displayText; // No color for unknown colors
        }
    }


    // ========== UTILITY METHODS ==========

    /**
     * Resets the piece to its home position (-1 pathPosition).
     * This method does NOT set the specific board coordinate; that must be handled
     * by the Board or game logic based on the piece's original home slot.
     *
     * @param homeBoardPosition The specific board (row, col) coordinates for this piece's home slot.
     */
    public void sendHome(Position homeBoardPosition) {
        this.pathPosition = -1; // Indicate it's at home
        this.currentBoardPosition = homeBoardPosition; // Set its specific board display coordinate
        logger.debug("{} was sent home to {}!", this.id, currentBoardPosition);
    }


    @Override
    public String toString() {
        return String.format("Piece{id='%s', color=%s, pathPos=%d, boardPos=%s}",
                id, color, pathPosition, currentBoardPosition);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Piece piece = (Piece) obj;
        return id.equals(piece.id); // Pieces are unique by their ID (color + number)
    }

    @Override
    public int hashCode() {
        return Objects.hash(id); // Hash code based on unique ID
    }


    public Position getStartingPosition() {
        return MovementPath.getCoordinateAt(this.color, 0);
    }
}