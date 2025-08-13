package com.ludo.ludo_server.game.connection;


/**
 * TurnManager - Manages turn order and validates player actions in multiplayer games
 *
 * Purpose: Enforces turn-based gameplay by tracking whose turn it is, what phase
 * the current turn is in, and validating that players can only act when appropriate.
 *
 * Key Responsibilities:
 * - Track current player and turn order
 * - Manage game phases (waiting for roll, choosing move, turn complete)
 * - Validate player actions based on turn state
 * - Handle turn switching and special cases (double 6 = extra turn)
 * - Store dice values and available moves for the current turn
 *
 * Turn Flow:
 * 1. Player rolls dice → phase becomes CHOOSING_MOVE
 * 2. Player selects move → execute move, check for turn end
 * 3. If no double 6 → switch to next player, phase becomes WAITING_FOR_ROLL
 * 4. If double 6 → same player continues, phase becomes WAITING_FOR_ROLL
 *
 * Used by: GameMessageController to validate and manage all game actions
 */

import com.ludo.ludo_server.player.Player;

import java.util.ArrayList;
import java.util.List;

public class TurnManager {

    private List<String> playerOrder;           // Order of player sessionIds
    private int currentPlayerIndex;             // Index of current player
    private GamePhase currentPhase;             // Current phase of the turn
    private List<Integer> availableDiceValues;  // Dice values available to use
    private boolean hasRolledDouble6;           // Track if player rolled double 6

    public TurnManager(List<Player> players) {
        this.playerOrder = new ArrayList<>();

        // Extract sessionIds from players (need to get from GameRoom)
        // For now, we'll initialize with player IDs and update later
        for (Player player : players) {
            this.playerOrder.add(player.getPlayerId());
        }

        this.currentPlayerIndex = 0;
        this.currentPhase = GamePhase.WAITING_FOR_ROLL;
        this.availableDiceValues = new ArrayList<>();
        this.hasRolledDouble6 = false;
    }

    // Initialize with actual session IDs (called after game setup)
    public void initializeWithSessions(List<String> sessionIds) {
        this.playerOrder = new ArrayList<>(sessionIds);
        this.currentPlayerIndex = 0;
    }

    // Get current player's session ID
    public String getCurrentPlayerSessionId() {
        if (playerOrder.isEmpty()) return null;
        return playerOrder.get(currentPlayerIndex);
    }

    // Check if it's this player's turn
    public boolean isPlayerTurn(String sessionId) {
        return sessionId.equals(getCurrentPlayerSessionId());
    }

    // Check if player can roll dice
    public boolean canRollDice(String sessionId) {
        return isPlayerTurn(sessionId) && currentPhase == GamePhase.WAITING_FOR_ROLL;
    }

    // Check if player can move a piece
    public boolean canMovePiece(String sessionId) {
        return isPlayerTurn(sessionId) && currentPhase == GamePhase.CHOOSING_MOVE;
    }

    // Process dice roll
    public void processDiceRoll(int die1, int die2) {
        availableDiceValues.clear();
        availableDiceValues.add(die1);
        availableDiceValues.add(die2);

        hasRolledDouble6 = (die1 == 6 && die2 == 6);
        currentPhase = GamePhase.CHOOSING_MOVE;
    }

    // Process piece move (remove used dice value)
    public boolean processPieceMove(int diceValue) {
        if (availableDiceValues.contains(diceValue)) {
            availableDiceValues.remove(Integer.valueOf(diceValue));

            // If no more dice values available, turn is complete
            if (availableDiceValues.isEmpty()) {
                completeTurn();
                return true; // Turn ended
            }
            return false; // Turn continues (more dice to use)
        }
        return false; // Invalid dice value
    }

    // Complete current turn and switch to next player
    private void completeTurn() {
        currentPhase = GamePhase.TURN_COMPLETE;

        // If double 6, same player gets another turn
        if (hasRolledDouble6) {
            // Same player, new turn
            currentPhase = GamePhase.WAITING_FOR_ROLL;
        } else {
            // Move to next player
            currentPlayerIndex = (currentPlayerIndex + 1) % playerOrder.size();
            currentPhase = GamePhase.WAITING_FOR_ROLL;
        }

        // Reset turn state
        availableDiceValues.clear();
        hasRolledDouble6 = false;
    }

    // Force end turn (for testing or game management)
    public void endTurn() {
        completeTurn();
    }

    // Get next player session ID
    public String getNextPlayerSessionId() {
        if (playerOrder.isEmpty()) return null;
        int nextIndex = (currentPlayerIndex + 1) % playerOrder.size();
        return playerOrder.get(nextIndex);
    }

    // Getters
    public GamePhase getCurrentPhase() {
        return currentPhase;
    }

    public List<Integer> getAvailableDiceValues() {
        return new ArrayList<>(availableDiceValues);
    }

    public boolean hasAvailableDice() {
        return !availableDiceValues.isEmpty();
    }

    public boolean isWaitingForRoll() {
        return currentPhase == GamePhase.WAITING_FOR_ROLL;
    }

    public boolean isChoosingMove() {
        return currentPhase == GamePhase.CHOOSING_MOVE;
    }

    // Get turn summary for debugging
    public String getTurnSummary() {
        return String.format("Player: %s, Phase: %s, Available dice: %s",
                getCurrentPlayerSessionId(), currentPhase, availableDiceValues);
    }
}
