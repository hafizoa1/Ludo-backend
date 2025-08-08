package com.ludo.ludo_server.board;


import com.ludo.ludo_server.piece.Piece;
import com.ludo.ludo_server.player.Player;
import com.ludo.ludo_server.player.PlayerColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Board class - Simple 15x15 grid representation of the Ludo board
 * Handles spatial layout and position tracking only
 * Game class handles all movement logic and rules
 */
public class Board {

    public static final int BOARD_SIZE = 15;

    // 15x15 grid of square types
    private final SquareType[][] grid;

    // Track which pieces are at which positions - Key: Board.Position, Value: Piece
    private final Map<Position, List<Piece>> piecePositions;

    // Stores all pieces managed by the board (for easy retrieval by ID, etc.)
    //private final List<Piece> allPieces;

    // Define fixed home yard positions for each piece.
    // These are the *display* coordinates for pieces at home.
    // MovementPath.getCoordinateAt(color, -1) should return these or map to them.
    // It's crucial this matches how you want them displayed initially.
    public static final Map<PlayerColor, Position[]> initialHomeYardCoords = new HashMap<>();

    static {
        // These coordinates are (row, col) as they appear on the visual board
        // Piece IDs R1, R2, R3, R4 map to these positions respectively.
        initialHomeYardCoords.put(PlayerColor.RED, new Position[]{
                new Position(2, 2), new Position(2, 3), // R1, R2
                new Position(3, 2), new Position(3, 3)  // R3, R4
        });
        initialHomeYardCoords.put(PlayerColor.GREEN, new Position[]{
                new Position(2, 11), new Position(2, 12), // G1, G2
                new Position(3, 11), new Position(3, 12)  // G3, G4
        });
        initialHomeYardCoords.put(PlayerColor.YELLOW, new Position[]{ // where blue is put yellow and where yellow is put blue
                new Position(11, 11), new Position(11, 12), // B1, B2
                new Position(12, 11), new Position(12, 12)  // B3, B4
        });
        initialHomeYardCoords.put(PlayerColor.BLUE, new Position[]{
                new Position(11, 2), new Position(11, 3), // Y1, Y2
                new Position(12, 2), new Position(12, 3)  // Y3, Y4
        });
    }

    /**
     * Constructor - initializes empty board and sets up Ludo layout
     */
    public Board(List<Player> players) {
        this.grid = new SquareType[BOARD_SIZE][BOARD_SIZE];
        this.piecePositions = new HashMap<>();
        initializeBoardLayout();
        placePiecesFromPlayers(players); // Instead of setupPieces()
    }


    /**
     * Initialize the standard Ludo board layout in the 15x15 grid
     */
    private void initializeBoardLayout() {
        // Start with WHITE everywhere (main track)
        fillGridWithWhite();

        // Override specific areas with their types
        setupHomeAreas();
        setupColoredEntryPoints();
        setupSafeHomeColumns();
        setupCenterArea();
    }

    /**
     * Fill entire grid with WHITE (main track spaces)
     */
    private void fillGridWithWhite() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                grid[row][col] = SquareType.WHITE;
            }
        }
    }

    /**
     * Set up the four corner home areas where pieces start
     */
    private void setupHomeAreas() {
        // RED home area (top-left corner)
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 6; col++) {
                grid[row][col] = SquareType.RED_HOME_AREA;
            }
        }

        // GREEN home area (top-right corner)
        for (int row = 0; row < 6; row++) {
            for (int col = 9; col < 15; col++) {
                grid[row][col] = SquareType.GREEN_HOME_AREA;
            }
        }

        // YELLOW home area (bottom-left corner)
        for (int row = 9; row < 15; row++) {
            for (int col = 0; col < 6; col++) {
                grid[row][col] = SquareType.BLUE_HOME_AREA; // make this blue
            }
        }

        // BLUE home area (bottom-right corner)
        for (int row = 9; row < 15; row++) {
            for (int col = 9; col < 15; col++) {
                grid[row][col] = SquareType.YELLOW_HOME_AREA; // make this yellow
            }
        }
    }

    /**
     * Set up colored entry points where pieces enter the main track
     * These are colored but capturable - any piece can land here
     */
    private void setupColoredEntryPoints() { // change yellow and blue
        grid[6][1] = SquareType.RED_TRACK;     // Red entry point
        grid[1][8] = SquareType.GREEN_TRACK;   // Green entry point
        grid[8][13] = SquareType.YELLOW_TRACK;   // Blue entry point
        grid[13][6] = SquareType.BLUE_TRACK; // Yellow entry point
    }

    /**
     * Set up safe home columns (final approach lanes)
     * Only that color's pieces can enter these
     */
    private void setupSafeHomeColumns() { // change yellow and blue
        // RED safe column (row 7, moving right toward center)
        for (int col = 1; col <= 5; col++) {
            grid[7][col] = SquareType.RED_SAFE;
        }

        // GREEN safe column (col 7, moving down toward center)
        for (int row = 1; row <= 5; row++) {
            grid[row][7] = SquareType.GREEN_SAFE;
        }

        // BLUE safe column (row 7, moving left toward center)
        for (int col = 9; col <= 13; col++) {
            grid[7][col] = SquareType.YELLOW_SAFE;
        }

        // YELLOW safe column (col 7, moving up toward center)
        for (int row = 9; row <= 13; row++) {
            grid[row][7] = SquareType.BLUE_SAFE;
        }
    }

    /**
     * Set up the center area where pieces finish
     */
    private void setupCenterArea() {
        // 3x3 center area
        for (int row = 6; row < 9; row++) {
            for (int col = 6; col < 9; col++) {
                grid[row][col] = SquareType.MIDDLE_AREA;
            }
        }
    }

    private void placePiecesFromPlayers(List<Player> players) {
        for (Player player : players) {
            for (Piece piece : player.getPieces()) {
                Position homePos = piece.getBoardPosition();
                piecePositions.computeIfAbsent(homePos, k -> new ArrayList<>()).add(piece);
            }
        }
    }


    // ========== PUBLIC INTERFACE METHODS ==========

    /**
     * Get the square type at given coordinates
     * @param row row coordinate (vertical)
     * @param col column coordinate (horizontal)
     * @return SquareType at that position, or EMPTY if invalid coordinates
     */
    public SquareType getSquareType(int row, int col) {
        if (isValidPosition(row, col)) {
            return grid[row][col];
        }
        return SquareType.EMPTY;
    }

    /**
     * Check if coordinates are within the board boundaries
     * @param row row coordinate (vertical)
     * @param col column coordinate (horizontal)
     * @return true if position is valid
     */
    public boolean isValidPosition(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    /**
     * Place a piece at the given position.
     * This method also updates the piece's internal board position.
     * @param piece the piece to place
     * @param row row coordinate (vertical)
     * @param col column coordinate (horizontal)
     */
    public void placePiece(Piece piece, int row, int col) {
        if (isValidPosition(row, col)) {
            Position position = new Position(row, col);
            piecePositions.computeIfAbsent(position, k -> new ArrayList<>()).add(piece);
            piece.setBoardPosition(position); // Update the piece's own record of its position
        } else {
            System.err.println("Error: Attempted to place piece " + piece.getId() + " at invalid board position: " + row + "," + col);
        }
    }


    public void removePiece(Piece piece) {
        Position position = piece.getBoardPosition();
        if (position != null && isValidPosition(position.getRow(), position.getCol())) {
            List<Piece> piecesAtPosition = piecePositions.get(position);
            if (piecesAtPosition != null) {
                piecesAtPosition.remove(piece);
                // Clean up empty lists to keep the map clean
                if (piecesAtPosition.isEmpty()) {
                    piecePositions.remove(position);
                }
            }
        }
    }

    public void removePiece(int row, int col) {
        if (isValidPosition(row, col)) {
            Position position = new Position(row, col);
            piecePositions.remove(position);
        }
    }

    /**
     * Get the piece at given coordinates
     * @param row row coordinate (vertical)
     * @param col column coordinate (horizontal)
     * @return Piece at that position, or null if no piece
     */
    public List<Piece> getPiecesAt(int row, int col) {
        if (isValidPosition(row, col)) {
            Position position = new Position(row, col);
            return piecePositions.getOrDefault(position, new ArrayList<>());
        }
        return new ArrayList<>();
    }



    /**
     * Check if there's a piece at the given position
     * @param row row coordinate (vertical)
     * @param col column coordinate (horizontal)
     * @return true if position is occupied
     */
    public boolean isOccupied(int row, int col) {
        return !getPiecesAt(row, col).isEmpty();
    }

    /**
     * Get all current piece positions (for display purposes)
     * @return copy of the piece positions map
     */
    public Map<Position, List<Piece>> getAllPiecePositions() {

        return this.piecePositions;
    }


    /**
     * Print the current board state to console
     * Shows both square types and pieces
     */
    public void printBoard() {
        System.out.println("\n=================== LUDO BOARD ===================");

        // Column numbers header
        System.out.print("    ");
        for (int col = 0; col < BOARD_SIZE; col++) {
            System.out.printf("%2d ", col);
        }
        System.out.println();

        // Print each row
        for (int row = 0; row < BOARD_SIZE; row++) {
            // Row number
            System.out.printf("%2d  ", row);

            // Print each square in the row
            for (int col = 0; col < BOARD_SIZE; col++) {
                List<Piece> pieces = getPiecesAt(row, col);

                // Special case: Center area (7,7) always shows * regardless of pieces
                if (row == 7 && col == 7) {
                    System.out.print("*  ");
                }
                else if (!pieces.isEmpty()) {
                    if (pieces.size() == 1) {
                        System.out.print(pieces.get(0).getColoredDisplay() + " ");
                    } else {
                        // Show count: "3x" for multiple pieces
                        System.out.print(pieces.size() + "x ");
                    }
                } else {
                    // Show square type if no piece
                    System.out.print(grid[row][col].getDisplayChar() + "  ");
                }
            }
            System.out.println();
        }

        System.out.println("==================================================\n");
    }


    /**
     * Print a simple legend explaining the board symbols
     */
    public void printLegend() {
        System.out.println("BOARD LEGEND:");
        System.out.println("[.] = White track (neutral)");
        System.out.println("[r][g][b][y] = Colored track (capturable)");
        System.out.println("[R][G][B][Y] = Safe home columns");
        System.out.println("[h] = Home areas");
        System.out.println("[*] = Center finish area");
        System.out.println("R1, G2, etc. = Player pieces");
        System.out.println();
    }
}