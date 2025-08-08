package com.ludo.ludo_server.piece;

/**
 package com.ludogame;

 /**
 * Enum representing the different states a piece can be in during the game
 */
public enum PieceState {
    /**
     * Piece is in the starting home area, waiting to enter the game
     * Needs a 6 to move out
     */
    HOME,

    /**
     * Piece is on the main playing track (52-square path)
     * Can move normally with any dice value
     */
    PLAYING,

    /**
     * Piece is in the safe home column (final approach to center)
     * Cannot be captured, moving toward finish
     */
    HOME_COLUMN,

    /**
     * Piece has reached the center and finished the game
     * Cannot move anymore
     */
    FINISHED;

    /**
     * Check if this state allows the piece to be captured
     * @return true if piece can be captured in this state
     */
    public boolean isVulnerable() {
        return this == PLAYING;
    }

    /**
     * Check if this state means the piece is safe from capture
     * @return true if piece cannot be captured in this state
     */
    public boolean isSafe() {
        return !isVulnerable();
    }

    /**
     * Check if the piece is actively in play (on the board)
     * @return true if piece is on the playing board
     */
    public boolean isInPlay() {
        return this == PLAYING || this == HOME_COLUMN;
    }

    /**
     * Check if the piece has completed the game
     * @return true if piece is finished
     */
    public boolean isCompleted() {
        return this == FINISHED;
    }

    /**
     * Get display string for this state
     * @return short description of the state
     */
    public String getDisplayString() {
        switch (this) {
            case HOME: return "Home";
            case PLAYING: return "Playing";
            case HOME_COLUMN: return "Home Column";
            case FINISHED: return "Finished";
            default: return "Unknown";
        }
    }
}
