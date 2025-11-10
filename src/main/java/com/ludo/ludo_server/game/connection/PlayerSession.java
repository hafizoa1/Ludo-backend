package com.ludo.ludo_server.game.connection;

import com.ludo.ludo_server.player.Player;
import lombok.Data;

import java.time.Instant;

/**
 * PlayerSession - Tracks a player's persistent identity across WebSocket sessions
 *
 * Purpose: Enable reconnection by separating persistent player identity (playerId)
 * from transient WebSocket session identity (sessionId).
 *
 * Key Concepts:
 * - playerId: Persistent device/browser ID (from localStorage) - DOESN'T CHANGE
 * - currentSessionId: Current WebSocket session - CHANGES on refresh/reconnect
 * - gameId: Which game the player is in
 * - status: Current connection state (CONNECTED, DISCONNECTED, LEFT)
 *
 * Lifecycle:
 * 1. Player joins game → Create PlayerSession with playerId + sessionId
 * 2. Player disconnects → Mark DISCONNECTED, start 30s timer
 * 3. Player reconnects → Update sessionId, mark CONNECTED
 * 4. Timer expires → Remove PlayerSession, declare opponent winner
 */
@Data
public class PlayerSession {

    private String playerId;           // Persistent device ID (e.g., "device-abc-123")
    private String currentSessionId;   // Current WebSocket session (changes on reconnect)
    private String gameId;             // Game this player is in
    private Player player;             // The actual game Player object
    private PlayerStatus status;       // Connection status
    private Instant lastSeen;          // Last activity timestamp
    private Instant createdAt;         // When this session was created

    public PlayerSession(String playerId, String sessionId, String gameId, Player player) {
        this.playerId = playerId;
        this.currentSessionId = sessionId;
        this.gameId = gameId;
        this.player = player;
        this.status = PlayerStatus.CONNECTED;
        this.lastSeen = Instant.now();
        this.createdAt = Instant.now();
    }

    /**
     * Update to a new WebSocket session (reconnection)
     */
    public void updateSession(String newSessionId) {
        this.currentSessionId = newSessionId;
        this.status = PlayerStatus.CONNECTED;
        this.lastSeen = Instant.now();
        System.out.println("🔄 Player " + playerId + " reconnected with new session: " + newSessionId);
    }

    /**
     * Mark as disconnected
     */
    public void markDisconnected() {
        this.status = PlayerStatus.DISCONNECTED;
        this.lastSeen = Instant.now();
        System.out.println("🔌 Player " + playerId + " disconnected from session: " + currentSessionId);
    }

    /**
     * Mark as left (intentional exit)
     */
    public void markLeft() {
        this.status = PlayerStatus.LEFT;
        this.lastSeen = Instant.now();
        System.out.println("👋 Player " + playerId + " left game: " + gameId);
    }

    /**
     * Check if player is in an active game
     */
    public boolean isInActiveGame() {
        return gameId != null && (status == PlayerStatus.CONNECTED || status == PlayerStatus.DISCONNECTED);
    }

    /**
     * Check if player is currently connected
     */
    public boolean isConnected() {
        return status == PlayerStatus.CONNECTED;
    }

    /**
     * Get display name from player
     */
    public String getDisplayName() {
        return player != null ? player.getPlayerName() : "Unknown";
    }
}
