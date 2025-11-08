package com.ludo.ludo_server.player;

import lombok.Getter;

/**
 * Represents the four player colors in Ludo
 * Each color corresponds to a different starting position and home area
 */
public enum  PlayerColor {

    RED("Red", "R", "\u001B[31m"),      // ANSI red
    BLUE("Blue", "B", "\u001B[34m"),    // ANSI blue
    GREEN("Green", "G", "\u001B[32m"),  // ANSI green
    YELLOW("Yellow", "Y", "\u001B[33m"); // ANSI yellow

    @Getter
    private final String fullName;
    @Getter
    private final String shortName;
    private final String ansiColor;

    private static final String ANSI_RESET = "\u001B[0m";

    PlayerColor(String fullName, String shortName, String ansiColor) {
        this.fullName = fullName;
        this.shortName = shortName;
        this.ansiColor = ansiColor;
    }

    @Override
    public String toString() {
        return fullName;
    }
}