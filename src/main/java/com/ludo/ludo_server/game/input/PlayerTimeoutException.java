package com.ludo.ludo_server.game.input;

/**
 * Exception thrown when a player doesn't respond to input request within timeout period
 */
public class PlayerTimeoutException extends RuntimeException {
    private final String sessionId;
    private final String playerName;

    // Note: playerId here is Player.getPlayerId() - the domain player's own id
    // (e.g. "player1"), not the persistent client id SessionMapper tracks for
    // reconnection. Only used to build this message, not exposed as a getter,
    // to avoid callers mixing it up with the other "playerId".
    public PlayerTimeoutException(String sessionId, String playerId, String playerName) {
        super("Player " + playerName + " (" + playerId + ") did not respond within timeout period");
        this.sessionId = sessionId;
        this.playerName = playerName;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getPlayerName() {
        return playerName;
    }
}
