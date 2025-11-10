package com.ludo.ludo_server.game.connection;

/**
 * PlayerStatus - Represents the connection state of a player
 *
 * CONNECTED - Player is actively connected via WebSocket
 * DISCONNECTED - Player disconnected (accidental) - waiting for reconnection
 * LEFT - Player intentionally left the game (clicked "Leave Game" button)
 */
public enum PlayerStatus {
    CONNECTED,      // Active WebSocket connection
    DISCONNECTED,   // Disconnected, waiting for reconnection (30s window)
    LEFT            // Intentionally left - no reconnection expected
}
