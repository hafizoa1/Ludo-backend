package com.ludo.ludo_server.piece;



import com.ludo.ludo_server.board.Position;
import com.ludo.ludo_server.player.PlayerColor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.ludo.ludo_server.board.Board.initialHomeYardCoords;


/**
 * MovementPath class - Manages and provides the pre-defined movement paths
 * for all player colors in the Ludo game.
 * This class acts as a central, static repository for all path data.
 */
public class MovementPath { // changfe movement paths for red and yellow

    // A static map to hold all initialized paths, mapped by PlayerColor
    private static final Map<PlayerColor, Map<Integer, Position>> allColorPaths = new HashMap<>();

    // Static initializer block: This code runs once when the class is first loaded.
    // It ensures all movement paths are created and stored at application startup.
    static {
        initializePaths();
    }

    // Private constructor to prevent external instantiation.
    // This enforces that MovementPath is used as a utility class with static methods.
    private MovementPath() {
        // No instantiation needed
    }

    /**
     * Initializes all the specific movement paths for each color.
     * Called automatically by the static initializer block.
     */
    private static void initializePaths() {
        // Populate the allColorPaths map with paths for each color
        allColorPaths.put(PlayerColor.RED, createRedPath());
        allColorPaths.put(PlayerColor.GREEN, createGreenPath());
        allColorPaths.put(PlayerColor.BLUE, createBluePath());
        allColorPaths.put(PlayerColor.YELLOW, createYellowPath());
    }

    /**
     * Public static method to retrieve the complete movement path for a given color.
     * The returned map is unmodifiable to prevent external alteration of the paths.
     *
     * @param color The PlayerColor enum for which to retrieve the path.
     * @return An unmodifiable Map where keys are path positions (int) and values are Position objects.
     * @throws IllegalArgumentException if the path for the given color has not been initialized.
     */
    public static Map<Integer, Position> getPathForColor(PlayerColor color) {
        // Retrieve the pre-computed path. If for some reason it's not there, throw an error.
        if (!allColorPaths.containsKey(color)) {
            throw new IllegalArgumentException("Path not initialized for color: " + color);
        }
        return Collections.unmodifiableMap(allColorPaths.get(color));
    }

    /**
     * Public static method to get the specific Position (row, col)
     * corresponding to a given path position for a specific color.
     * NOTE: This method does NOT handle the '-1' (home) position.
     * The Piece class or Board class should manage the specific home yard
     * coordinates for individual pieces. This class provides the sequential
     * path from position 0 up to the finish.
     *
     * @param color The PlayerColor of the piece.
     * @param pathPosition The position on the piece's movement path (0+ for on-board).
     * @return The Position coordinates, or null if the pathPosition is invalid or if it's the home position (-1).
     */
    public static Position getCoordinateAt(PlayerColor color, int pathPosition) {
        // This method is now specifically for on-board path positions (0 to MAX_PATH_POSITION).
        // Home positions (-1) are handled by the Board/Piece based on initial yard coords.
        if (pathPosition == -1) {
            // For a piece at home (-1 path position), its board coordinate is its specific yard slot.
            // MovementPath doesn't know which specific yard slot it is.
            // The Board or Piece should explicitly set this when a piece is sent home.
            // Returning null here will signal that this path cannot provide a coordinate for -1.
            //initialHomeYardCoords.get(color);
            return null;
        }

        Map<Integer, Position> path = getPathForColor(color);
        return path.get(pathPosition);
    }

    public static Position getCoordinateAt(int pieceNumber, PlayerColor color) {
       Position[] homePositions =  initialHomeYardCoords.get(color);
       return homePositions[pieceNumber - 1]; // lets start with one
    }


    // --- Private Static Methods for Creating Individual Color Paths ---
    // These methods encapsulate the complex logic of defining each color's specific path.

    private static Map<Integer, Position> createRedPath() {
        HashMap <Integer, Position> redPath = new HashMap<>();
        int i = 0; // pathPosition counter

        // RED STARTS AT (6,1) - Red entry point on main track (PathPosition 0)
        redPath.put(i++, new Position(6, 1));

        // Segment 1: Move RIGHT along row 6 (6,2) → (6,5)
        for (int col = 2; col <= 5; col++) {
            redPath.put(i++, new Position(6, col));
        }

        // Segment 2: Move UP column 6 (5,6) → (0,6)
        for (int row = 5; row >= 0; row--) {
            redPath.put(i++, new Position(row, 6));
        }

        // Segment 3: Move RIGHT along row 0 (0,7) → (0,8)
        for (int col = 7; col <= 8; col++) {
            redPath.put(i++, new Position(0, col));
        }

        // Segment 4: Move DOWN column 8 (1,8) → (5,8)
        for (int row = 1; row <= 5; row++) {
            redPath.put(i++, new Position(row, 8));
        }

        // Segment 5: Move RIGHT along row 6 (6,9) → (6,14)
        for (int col = 9; col <= 14; col++) {
            redPath.put(i++, new Position(6, col));
        }

        // Segment 6: Move DOWN column 14 (7,14) → (8,14)
        for (int row = 7; row <= 8; row++) {
            redPath.put(i++, new Position(row, 14));
        }

        // Segment 7: Move LEFT along row 8 (8,13) → (8,9)
        for (int col = 13; col >= 9; col--) {
            redPath.put(i++, new Position(8, col));
        }

        // Segment 8: Move DOWN column 8 (9,8) → (14,8)
        for (int row = 9; row <= 14; row++) {
            redPath.put(i++, new Position(row, 8));
        }

        // Segment 9: Move LEFT along row 14 (14,7) → (14,6)
        for (int col = 7; col >= 6; col--) {
            redPath.put(i++, new Position(14, col));
        }

        // Segment 10: Move UP column 6 (13,6) → (9,6)
        for (int row = 13; row >= 9; row--) {
            redPath.put(i++, new Position(row, 6));
        }

        // Segment 11: Move LEFT along row 8 (8,5) → (8,1) - This is the last square before entering Red's safe path
        for (int col = 5; col >= 1; col--) {
            redPath.put(i++, new Position(8, col));
        }

        // RED SAFE COLUMN: Move RIGHT along row 7 (7,1) → (7,6)
        // These are the 6 safe squares for red.
        for (int col = 1; col <= 6; col++) {
            redPath.put(i++, new Position(7, col));
        }

        // Finish Point: (7,7) - The very last position a piece lands on.
        redPath.put(i++, new Position(7, 7)); // This will be pathPosition 56 if MAX_PATH_POSITION is 55.

        // Return an unmodifiable map to ensure the path cannot be altered after creation
        return Collections.unmodifiableMap(redPath);
    }

    private static Map<Integer, Position> createGreenPath() {
        HashMap <Integer, Position> greenPath = new HashMap<>();
        int i = 0; // pathPosition counter

        // GREEN STARTS AT (1,8) - Green entry point on main track (PathPosition 0)
        greenPath.put(i++, new Position(1, 8));

        // Segment 1: Move DOWN along column 8 (2,8) → (5,8)
        for (int row = 2; row <= 5; row++) {
            greenPath.put(i++, new Position(row, 8));
        }

        // Segment 2: Move RIGHT along row 6 (6,9) → (6,14)
        for (int col = 9; col <= 14; col++) {
            greenPath.put(i++, new Position(6, col));
        }

        // Segment 3: Move DOWN along column 14 (7,14) → (8,14)
        for (int row = 7; row <= 8; row++) {
            greenPath.put(i++, new Position(row, 14));
        }

        // Segment 4: Move LEFT along row 8 (8,13) → (8,9)
        for (int col = 13; col >= 9; col--) {
            greenPath.put(i++, new Position(8, col));
        }

        // Segment 5: Move DOWN along column 8 (9,8) → (14,8)
        for (int row = 9; row <= 14; row++) {
            greenPath.put(i++, new Position(row, 8));
        }

        // Segment 6: Move LEFT along row 14 (14,7) → (14,6)
        for (int col = 7; col >= 6; col--) {
            greenPath.put(i++, new Position(14, col));
        }

        // Segment 7: Move UP along column 6 (13,6) → (9,6)
        for (int row = 13; row >= 9; row--) {
            greenPath.put(i++, new Position(row, 6));
        }

        // Segment 8: Move LEFT along row 8 (8,5) → (8,0)
        for (int col = 5; col >= 0; col--) {
            greenPath.put(i++, new Position(8, col));
        }

        // Segment 9: Move UP along column 0 (7,0) → (6,0)
        for (int row = 7; row >= 6; row--) {
            greenPath.put(i++, new Position(row, 0));
        }

        // Segment 10: Move RIGHT along row 6 (6,1) → (6,5)
        for (int col = 1; col <= 5; col++) {
            greenPath.put(i++, new Position(6, col));
        }

        // Segment 11: Move UP along column 6 (5,6) → (1,6) - Last square before Green's safe path
        for (int row = 5; row >= 1; row--) {
            greenPath.put(i++, new Position(row, 6));
        }

        // GREEN SAFE COLUMN: Move DOWN along column 7 (1,7) → (6,7)
        for (int row = 1; row <= 6; row++) {
            greenPath.put(i++, new Position(row, 7));
        }

        // Finish Point: (7,7)
        greenPath.put(i++, new Position(7, 7));

        return Collections.unmodifiableMap(greenPath);
    }

    private static Map<Integer, Position> createYellowPath() {
        HashMap<Integer, Position> yellowPath = new HashMap<>();
        int i = 0;

        // BLUE STARTS AT (8,13) - Blue entry point on main track (PathPosition 0)
        yellowPath.put(i++, new Position(8, 13));

        // Segment 1: Move LEFT along row 8 (8,12) → (8,9)
        for (int col = 12; col >= 9; col--) {
            yellowPath.put(i++, new Position(8, col));
        }

        // Segment 2: Move DOWN along column 8 (9,8) → (14,8)
        for (int row = 9; row <= 14; row++) {
            yellowPath.put(i++, new Position(row, 8));
        }

        // Segment 3: Move LEFT along row 14 (14,7) → (14,6)
        for (int col = 7; col >= 6; col--) {
            yellowPath.put(i++, new Position(14, col));
        }

        // Segment 4: Move UP along column 6 (13,6) → (9,6)
        for (int row = 13; row >= 9; row--) {
            yellowPath.put(i++, new Position(row, 6));
        }

        // Segment 5: Move LEFT along row 8 (8,5) → (8,0)
        for (int col = 5; col >= 0; col--) {
            yellowPath.put(i++, new Position(8, col));
        }

        // Segment 6: Move UP along column 0 (7,0) → (6,0)
        for (int row = 7; row >= 6; row--) {
            yellowPath.put(i++, new Position(row, 0));
        }

        // Segment 7: Move RIGHT along row 6 (6,1) → (6,5)
        for (int col = 1; col <= 5; col++) {
            yellowPath.put(i++, new Position(6, col));
        }

        // Segment 8: Move UP along column 6 (5,6) → (0,6)
        for (int row = 5; row >= 0; row--) {
            yellowPath.put(i++, new Position(row, 6));
        }

        // Segment 9: Move RIGHT along row 0 (0,7) → (0,8)
        for (int col = 7; col <= 8; col++) {
            yellowPath.put(i++, new Position(0, col));
        }

        // Segment 10: Move DOWN along column 8 (1,8) → (5,8)
        for (int row = 1; row <= 5; row++) {
            yellowPath.put(i++, new Position(row, 8));
        }

        // Segment 11: Move RIGHT along row 6 (6,9) → (6,14)
        for (int col = 9; col <= 14; col++) {
            yellowPath.put(i++, new Position(6, col));
        }

        // Segment 12: Move DOWN from (6,14) to (7,14), then LEFT along row 7 (7,13) → (7,9)
        yellowPath.put(i++, new Position(7, 14));
        for (int col = 13; col >= 9; col--) {
            yellowPath.put(i++, new Position(7, col));
        }

        // BLUE SAFE ROW: Move LEFT along row 7 from (7,13) → (7,8)
        for (int col = 13; col >= 8; col--) {
            yellowPath.put(i++, new Position(7, col));
        }

        // Finish Point: (7,7) - The very last position a piece lands on.
        yellowPath.put(i++, new Position(7, 7));

        return Collections.unmodifiableMap(yellowPath);
    }

    private static Map<Integer, Position> createBluePath() {
        HashMap <Integer, Position> bluePath = new HashMap<>();
        int i = 0;

        // YELLOW STARTS AT (13,6) - Blue entry point on main track (PathPosition 0)
        bluePath.put(i++, new Position(13, 6));

        // Segment 1: Move UP along column 6 (12,6) → (9,6)
        for (int row = 12; row >= 9; row--) {
            bluePath.put(i++, new Position(row, 6));
        }

        // Segment 2: Move LEFT along row 8 (8,5) → (8,0)
        for (int col = 5; col >= 0; col--) {
            bluePath.put(i++, new Position(8, col));
        }

        // Segment 3: Move UP along column 0 (7,0) → (6,0)
        for (int row = 7; row >= 6; row--) {
            bluePath.put(i++, new Position(row, 0));
        }

        // Segment 4: Move RIGHT along row 6 (6,1) → (6,5)
        for (int col = 1; col <= 5; col++) {
            bluePath.put(i++, new Position(6, col));
        }

        // Segment 5: Move UP along column 6 (5,6) → (1,6)
        for (int row = 5; row >= 1; row--) {
            bluePath.put(i++, new Position(row, 6));
        }

        // Segment 6: Move RIGHT along row 0 (0,7) → (0,8)
        for (int col = 7; col <= 8; col++) {
            bluePath.put(i++, new Position(0, col));
        }

        // Segment 7: Move DOWN along column 8 (1,8) → (5,8)
        for (int row = 1; row <= 5; row++) {
            bluePath.put(i++, new Position(row, 8));
        }

        // Segment 8: Move RIGHT along row 6 (6,9) → (6,14)
        for (int col = 9; col <= 14; col++) {
            bluePath.put(i++, new Position(6, col));
        }

        // Segment 9: Move DOWN along column 14 (7,14) → (8,14)
        for (int row = 7; row <= 8; row++) {
            bluePath.put(i++, new Position(row, 14));
        }

        // Segment 10: Move LEFT along row 8 (8,13) → (8,9)
        for (int col = 13; col >= 9; col--) {
            bluePath.put(i++, new Position(8, col));
        }

        // Segment 11: Move DOWN along column 8 (9,8) → (13,8) - Last square before Blue's safe path
        for (int row = 9; row <= 13; row++) {
            bluePath.put(i++, new Position(row, 8));
        }


        // Corrected YELLOW SAFE COLUMN: Move UP along column 7 (13,7) → (8,7)
        for (int row = 13; row >= 8; row--) { // Move UP row 13 down to row 8 in col 7
            bluePath.put(i++, new Position(row, 7));
        }


        // Finish Point: (7,7)
        bluePath.put(i++, new Position(7, 7));

        return Collections.unmodifiableMap(bluePath);
    }
}