package com.ludo.ludo_server.board;

/**
 * Enum representing different types of squares on the Ludo board.
 * Each square type has different rules and behaviors for piece movement.
 */
public enum SquareType {
    /**
     * Regular neutral track spaces that any player can land on
     */
    WHITE,

    /**
     * Red-colored track squares - visually red but any piece can land/capture here
     */
    RED_TRACK,

    /**
     * Green-colored track squares - visually green but any piece can land/capture here
     */
    GREEN_TRACK,

    /**
     * Blue-colored track squares - visually blue but any piece can land/capture here
     */
    BLUE_TRACK,

    /**
     * Yellow-colored track squares - visually yellow but any piece can land/capture here
     */
    YELLOW_TRACK,

    /**
     * Red safe home column - only red pieces can enter (final approach)
     */
    RED_SAFE,

    /**
     * Green safe home column - only green pieces can enter (final approach)
     */
    GREEN_SAFE,

    /**
     * Blue safe home column - only blue pieces can enter (final approach)
     */
    BLUE_SAFE,

    /**
     * Yellow safe home column - only yellow pieces can enter (final approach)
     */
    YELLOW_SAFE,

    /**
     * Red player's starting yard where red pieces begin
     * Only red pieces can be placed here
     */
    RED_HOME_AREA,

    /**
     * Green player's starting yard where green pieces begin
     * Only green pieces can be placed here
     */
    GREEN_HOME_AREA,

    /**
     * Blue player's starting yard where blue pieces begin
     * Only blue pieces can be placed here
     */
    BLUE_HOME_AREA,

    /**
     * Yellow player's starting yard where yellow pieces begin
     * Only yellow pieces can be placed here
     */
    YELLOW_HOME_AREA,

    /**
     * Center destination area where pieces finish
     * Unplayable for movement - pieces stored here when finished
     */
    MIDDLE_AREA,

    /**
     * Off-board empty spaces in the 15x15 grid
     * No pieces can be placed here
     */
    EMPTY;


    /**
     * Get the display character for console output
     * Uses simple ASCII characters that work across all terminals
     * @return character representation of this square type
     */
    public char getDisplayChar() {
        switch (this) {
            case WHITE: return '.';                  // Neutral track spaces
            case RED_TRACK: return 'r';              // Red track (lowercase)
            case GREEN_TRACK: return 'g';            // Green track (lowercase)
            case BLUE_TRACK: return 'b';             // Blue track (lowercase)
            case YELLOW_TRACK: return 'y';           // Yellow track (lowercase)
            case RED_SAFE: return 'R';               // Red safe column (uppercase)
            case GREEN_SAFE: return 'G';             // Green safe column (uppercase)
            case BLUE_SAFE: return 'B';              // Blue safe column (uppercase)
            case YELLOW_SAFE: return 'Y';            // Yellow safe column (uppercase)
            case RED_HOME_AREA: return 'h';          // Home areas
            case GREEN_HOME_AREA: return 'h';
            case BLUE_HOME_AREA: return 'h';
            case YELLOW_HOME_AREA: return 'h';
            case MIDDLE_AREA: return '*';            // Center finish area
            case EMPTY: return ' ';                  // Empty grid space
            default: return '?';
        }
    }

    /**
     * Get colored display for terminal board printing with ANSI colors
     * @return colored display string with ANSI escape codes
     */
    public String getColoredDisplay() {
        // ANSI color codes
        final String RED = "\u001B[31m";
        final String GREEN = "\u001B[32m";
        final String BLUE = "\u001B[34m";
        final String YELLOW = "\u001B[33m";
        final String WHITE_COLOR = "\u001B[37m";
        final String BRIGHT_RED = "\u001B[91m";
        final String BRIGHT_GREEN = "\u001B[92m";
        final String BRIGHT_BLUE = "\u001B[94m";
        final String BRIGHT_YELLOW = "\u001B[93m";
        final String RESET = "\u001B[0m";

        switch (this) {
            case WHITE:
                return WHITE_COLOR + "[. ]" + RESET;
            case RED_TRACK:
                return RED + "[r ]" + RESET;
            case GREEN_TRACK:
                return GREEN + "[g ]" + RESET;
            case BLUE_TRACK:
                return BLUE + "[b ]" + RESET;
            case YELLOW_TRACK:
                return YELLOW + "[y ]" + RESET;
            case RED_SAFE:
                return BRIGHT_RED + "[R ]" + RESET;
            case GREEN_SAFE:
                return BRIGHT_GREEN + "[G ]" + RESET;
            case BLUE_SAFE:
                return BRIGHT_BLUE + "[B ]" + RESET;
            case YELLOW_SAFE:
                return BRIGHT_YELLOW + "[Y ]" + RESET;
            case RED_HOME_AREA:
                return RED + "[h ]" + RESET;
            case GREEN_HOME_AREA:
                return GREEN + "[h ]" + RESET;
            case BLUE_HOME_AREA:
                return BLUE + "[h ]" + RESET;
            case YELLOW_HOME_AREA:
                return YELLOW + "[h ]" + RESET;
            case MIDDLE_AREA:
                return WHITE_COLOR + "[* ]" + RESET;
            case EMPTY:
                return "    ";
            default:
                return "[? ]";
        }
    }

}