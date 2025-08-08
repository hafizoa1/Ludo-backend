package com.ludo.ludo_server.player;

/**
 * Represents the four player colors in Ludo
 * Each color corresponds to a different starting position and home area
 */
public enum  PlayerColor {

    RED("Red", "R", "\u001B[31m"),      // ANSI red
    BLUE("Blue", "B", "\u001B[34m"),    // ANSI blue
    GREEN("Green", "G", "\u001B[32m"),  // ANSI green
    YELLOW("Yellow", "Y", "\u001B[33m"); // ANSI yellow

    private final String fullName;
    private final String shortName;
    private final String ansiColor;

    private static final String ANSI_RESET = "\u001B[0m";

    PlayerColor(String fullName, String shortName, String ansiColor) {
        this.fullName = fullName;
        this.shortName = shortName;
        this.ansiColor = ansiColor;
    }

    public String getFullName() {
        return fullName;
    }

    public String getShortName() {
        return shortName;
    }

    public String getAnsiColor() {
        return ansiColor;
    }

    /**
     * Get the display character for this color
     */
    public char getDisplayChar() {
        return shortName.charAt(0);
    }

    /**
     * Get colored text for terminal display
     */
    public String colorize(String text) {
        return ansiColor + text + ANSI_RESET;
    }

    /**
     * Get a colored version of the short name
     */
    public String getColoredShortName() {
        return colorize(shortName);
    }

    /**
     * Get a colored version of the full name
     */
    public String getColoredFullName() {
        return colorize(fullName);
    }

    /**
     * Get the next color in clockwise order (for turn management)
     */
    public PlayerColor getNextColor() {
        PlayerColor[] colors = values();
        int currentIndex = this.ordinal();
        return colors[(currentIndex + 1) % colors.length];
    }

    /**
     * Get the previous color in counter-clockwise order
     */
    public PlayerColor getPreviousColor() {
        PlayerColor[] colors = values();
        int currentIndex = this.ordinal();
        return colors[(currentIndex - 1 + colors.length) % colors.length];
    }

    /**
     * Get all colors in the standard playing order
     */
    public static PlayerColor[] getPlayingOrder() {
        return new PlayerColor[]{RED, BLUE, GREEN, YELLOW};
    }

    /**
     * Get a color by its short name
     */
    public static PlayerColor fromShortName(String shortName) {
        for (PlayerColor color : values()) {
            if (color.shortName.equalsIgnoreCase(shortName)) {
                return color;
            }
        }
        throw new IllegalArgumentException("Unknown color short name: " + shortName);
    }

    /**
     * Get a color by its full name
     */
    public static PlayerColor fromFullName(String fullName) {
        for (PlayerColor color : values()) {
            if (color.fullName.equalsIgnoreCase(fullName)) {
                return color;
            }
        }
        throw new IllegalArgumentException("Unknown color full name: " + fullName);
    }

    /**
     * Check if this color is opposite to another color on the board
     */
    public boolean isOpposite(PlayerColor other) {
        return (this == RED && other == GREEN) ||
                (this == GREEN && other == RED) ||
                (this == BLUE && other == YELLOW) ||
                (this == YELLOW && other == BLUE);
    }

    /**
     * Get the opposite color on the board
     */
    public PlayerColor getOpposite() {
        switch (this) {
            case RED: return GREEN;
            case GREEN: return RED;
            case BLUE: return YELLOW;
            case YELLOW: return BLUE;
            default: throw new IllegalStateException("Unknown color: " + this);
        }
    }

    @Override
    public String toString() {
        return fullName;
    }
}