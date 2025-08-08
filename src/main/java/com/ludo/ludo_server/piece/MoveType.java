package com.ludo.ludo_server.piece;

public enum MoveType {
    BRING_OUT,    // Bringing a piece out of home (requires 6)
    NORMAL,       // Regular move using one die
    COMBINED,     // Move using sum of both dice
    ENTER_HOME,   // Moving into the home column
    FINISH
}
