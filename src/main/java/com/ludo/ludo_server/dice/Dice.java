package com.ludo.ludo_server.dice;

import java.util.Random;

/**
 * Simple dice implementation for Ludo
 * Handles rolling two dice independently
 */
public class Dice { // we want to calculate if the die is used or not

    private final Random random;
    private int die1;
    private int die2;

    // For testing
    private boolean testMode;
    private int[] testRolls;
    private int testIndex;

    public Dice() {
        this.random = new Random();
        this.die1 = 0;
        this.die2 = 0;
        this.testMode = false;
    }

    /**
     * Roll both dice
     */
    public void roll() {
        if (testMode && testIndex < testRolls.length - 1) {
            die1 = testRolls[testIndex++];
            die2 = testRolls[testIndex++];
        } else {
            die1 = random.nextInt(6) + 1;
            die2 = random.nextInt(6) + 1;
        }
    }

    /**
     * Get first die value
     */
    public int getDie1() {
        return die1;
    }

    /**
     * Get second die value
     */
    public int getDie2() {
        return die2;
    }

    /**
     * Get both dice values as array [die1, die2]
     */
    public int[] getBothDice() {
        return new int[]{die1, die2};
    }

    /**
     * Check if either die is a 6
     */
    public boolean hasSix() {
        return die1 == 6 || die2 == 6;
    }

    /**
     * Check if both dice are 6
     */
    public boolean isDoubleSix() {
        return die1 == 6 && die2 == 6;
    }

    /**
     * Check if both dice show the same value
     */
    public boolean isDouble() {
        return die1 == die2;
    }

    /**
     * Get total of both dice
     */
    public int getTotal() {
        return die1 + die2;
    }

    // ========== TESTING METHODS ==========

    /**
     * Set predetermined rolls for testing
     * Array should have even number of elements: [die1, die2, die1, die2, ...]
     */
    public void setTestRolls(int... rolls) {
        if (rolls.length % 2 != 0) {
            throw new IllegalArgumentException("Test rolls must have even number of elements");
        }
        this.testRolls = rolls.clone();
        this.testIndex = 0;
        this.testMode = true;
    }

    /**
     * Disable test mode (return to random rolling)
     */
    public void disableTestMode() {
        this.testMode = false;
        this.testRolls = null;
        this.testIndex = 0;
    }

    /**
     * Check if in test mode
     */
    public boolean isTestMode() {
        return testMode;
    }

    @Override
    public String toString() {
        if (die1 == 0 && die2 == 0) {
            return "Dice (not rolled yet)";
        }
        return String.format("Dice (%d, %d)", die1, die2);
    }
}

