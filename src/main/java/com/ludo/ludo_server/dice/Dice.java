package com.ludo.ludo_server.dice;

import lombok.Getter;

import java.util.Random;

/**
 * Simple dice implementation for Ludo
 * Handles rolling two dice independently
 */
public class Dice { // we want to calculate if the die is used or not

    private final Random random;
    /**
     * -- GETTER --
     *  Get first die value
     */
    @Getter
    private int die1;
    /**
     * -- GETTER --
     *  Get second die value
     */
    @Getter
    private int die2;


    public Dice() {
        this.random = new Random();
        this.die1 = 0;
        this.die2 = 0;
    }

    /**
     * Roll both dice
     */
    public void roll() {
        die1 = random.nextInt(6) + 1;
        die2 = random.nextInt(6) + 1;
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


    @Override
    public String toString() {
        if (die1 == 0 && die2 == 0) {
            return "Dice (not rolled yet)";
        }
        return String.format("Dice (%d, %d)", die1, die2);
    }
}

