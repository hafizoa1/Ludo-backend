package com.ludo.ludo_server.game.input;

/**
 * Exception thrown when a player doesn't respond to input request within timeout period
 */
public class PlayerTimeoutException extends RuntimeException {
    private final String sessionId;
    private final String playerId;
    private final String playerName;

    public PlayerTimeoutException(String sessionId, String playerId, String playerName) {
        super("Player " + playerName + " (" + playerId + ") did not respond within timeout period");
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }
}
