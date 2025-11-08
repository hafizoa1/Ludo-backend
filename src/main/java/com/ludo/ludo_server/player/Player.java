package com.ludo.ludo_server.player;


import com.ludo.ludo_server.board.Board;
import com.ludo.ludo_server.board.Position;
import com.ludo.ludo_server.piece.Piece;

import java.util.*;

/**
 * Abstract base class for all players
 */
public abstract class Player {

    protected final String playerId;
    protected final String playerName;
    protected final List<PlayerColor> colors;
    protected final Piece[] pieces;

    public static final int PIECES_PER_PLAYER = 4;

    public Player(String playerId, String playerName, PlayerColor color) {
        this(playerId, playerName, Arrays.asList(color));
    }

    // Multiple colors constructor
    public Player(String playerId, String playerName, List<PlayerColor> colors) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.colors = colors;
        this.pieces = new Piece[PIECES_PER_PLAYER * colors.size()]; // Scale array size
        initializePieces();
    }

    private void initializePieces() {
        int pieceIndex = 0;
        for (PlayerColor color : colors) {
            Position[] homeCoords = Board.initialHomeYardCoords.get(color);
            for (int i = 0; i < PIECES_PER_PLAYER; i++) {
                pieces[pieceIndex] = new Piece(color, i + 1);
                pieces[pieceIndex].setBoardPosition(homeCoords[i]);
                pieceIndex++;
            }
        }
    }


    // Getters
    public String getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public List<PlayerColor> getColors() { return colors; }

    public Piece[] getPieces() {
        return pieces.clone();
    }

    public List<Piece> getActivePieces() {

        Piece[] allPieces = getPieces(); // Assuming this returns an array
        List<Piece> activePieces = new ArrayList<>(); // Use ArrayList, it's dynamic!

        for (Piece piece : allPieces) {
            // Assuming 'isActive()' is a method on your Piece class
            // ,or you have logic here to determine if a piece is active (e.g., not HOME_BASE, not FINISHED)
            if (piece.getPathPosition() != -1) {
                activePieces.add(piece);
            }
        }
        return activePieces;
    }


    /**
     * Gets all pieces that are currently at home (not yet brought out)
     * @return List of pieces that are still in the home base
     */
    public List<Piece> getHomePieces() {
        Piece[] allPieces = getPieces();
        List<Piece> homePieces = new ArrayList<>();

        for (Piece piece : allPieces) {
            // A piece is at home if its path position is -1 (not on the board yet)
            if (piece.getPathPosition() == -1) {
                homePieces.add(piece);
            }
        }

        return homePieces;
    }

    /**
     * Gets all pieces that have finished the game (reached the end)
     * @return List of pieces that have completed their journey
     */
    public List<Piece> getFinishedPieces() {
        Piece[] allPieces = getPieces();
        List<Piece> finishedPieces = new ArrayList<>();

        for (Piece piece : allPieces) {
            // Assuming finished pieces have a specific path position or state
            // You'll need to adjust this based on how you track finished pieces
            if (piece.isFinished()) { // or whatever method indicates completion
                finishedPieces.add(piece);
            }
        }

        return finishedPieces;
    }

    /**
     * Checks if this player has won the game (all pieces finished)
     * @return true if all pieces have reached the finish
     */
    public boolean hasWon() {
        return getFinishedPieces().size() == pieces.length;
    }

    /**
     * Gets count of pieces in different states for quick reference
     * @return Map with counts of pieces in each state
     */
    public Map<String, Integer> getPieceStatusCounts() {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("home", getHomePieces().size());
        counts.put("active", getActivePieces().size());
        counts.put("finished", getFinishedPieces().size());
        return counts;
    }

    // Abstract methods that subclasses must implement
    public abstract boolean isHuman();


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Player player = (Player) obj;
        return Objects.equals(playerId, player.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId);
    }
}
