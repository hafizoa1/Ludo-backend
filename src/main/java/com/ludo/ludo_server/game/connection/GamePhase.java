package com.ludo.ludo_server.game.connection;


/**
 * GamePhase - Enum representing different phases within a player's turn
 *
 * Purpose: Controls what actions are valid at each stage of a turn, preventing
 * players from performing actions out of sequence (e.g., can't move before rolling dice).
 *
 * Phases:
 * - WAITING_FOR_ROLL: Player needs to roll dice to start their turn
 * - CHOOSING_MOVE: Player has rolled dice and must select which piece to move
 * - TURN_COMPLETE: Player has finished their turn, switching to next player
 *
 * Flow: WAITING_FOR_ROLL → roll dice → CHOOSING_MOVE → select move → TURN_COMPLETE → next player
 *
 * Used by: TurnManager to validate player actions and enforce turn-based gameplay
 */

public enum GamePhase {
    WAITING_FOR_ROLL,    // Player must roll dice to start turn
    CHOOSING_MOVE,       // Player has rolled, must choose which piece/die to use
    TURN_COMPLETE        // Turn finished, ready to switch to next player
}
