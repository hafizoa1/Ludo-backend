package com.ludo.ludo_server.config;



import com.ludo.ludo_server.game.input.InputProvider;
import com.ludo.ludo_server.player.ComputerPlayer;
import com.ludo.ludo_server.player.HumanPlayer;
import com.ludo.ludo_server.player.Player;
import com.ludo.ludo_server.player.PlayerColor;

import java.util.*;

public class GameConfig {
    private List<Player> players;
    private String gameType;
    private final InputProvider inputProvider;

    public GameConfig(InputProvider inputProvider) {
        this.inputProvider = inputProvider;
        this.players = new ArrayList<>();
        this.gameType = "NORMAL";
    }

    public GameConfig(List<Player> players, String gameType, InputProvider inputProvider) {
        this.players = players;
        this.gameType = gameType;
        this.inputProvider = inputProvider;
    }

    /**
     * Main configuration method - prompts user for game setup
     */
    public List<Player> configureGame() { //REMEMBER TO USE WSS
        System.out.println("=== LUDO GAME CONFIGURATION ===");

        // Step 1: Choose number of players
        int playerCount = choosePlayerCount();

        // Step 2: Choose human vs computer distribution
        PlayerDistribution distribution = choosePlayerDistribution(playerCount);

        // Step 3: If computers are involved, choose difficulty
        ComputerDifficulty difficulty = ComputerDifficulty.EASY;
        if (distribution.computerCount > 0) {
            difficulty = chooseDifficulty();
        }

        // Step 4: Create players with assigned colors
        createPlayersWithColors(distribution, difficulty);

        System.out.println("\n=== GAME SETUP COMPLETE ===");
        displayPlayerSummary();

        return this.players;
    }

    /**
     * Choose 2 or 4 players
     */
    private int choosePlayerCount() {
        System.out.println("Choose game type:");
        System.out.println("1. 2 Player Game");
        System.out.println("2. 4 Player Game");

        int choice = getValidChoice(1, 2, "Enter choice (1-2): ");
        return choice == 1 ? 2 : 4;
    }

    /**
     * Choose human vs computer distribution
     */
    private PlayerDistribution choosePlayerDistribution(int totalPlayers) {
        System.out.println("\nChoose player types:");

        if (totalPlayers == 2) {
            System.out.println("1. Human vs Human");
            System.out.println("2. Human vs Computer");

            int choice = getValidChoice(1, 2, "Enter choice (1-2): ");

            if (choice == 1) {
                this.gameType = "2 Human Players";
                return new PlayerDistribution(2, 0);
            } else {
                this.gameType = "Human vs Computer";
                return new PlayerDistribution(1, 1);
            }
        } else { // 4 players
            System.out.println("1. 4 Human Players");
            System.out.println("2. 3 Human Players, 1 Computer");
            System.out.println("3. 2 Human Players, 2 Computers");
            System.out.println("4. 1 Human Player, 3 Computers");

            int choice = getValidChoice(1, 4, "Enter choice (1-4): ");

            switch (choice) {
                case 1:
                    this.gameType = "4 Human Players";
                    return new PlayerDistribution(4, 0);
                case 2:
                    this.gameType = "3 Humans, 1 Computer";
                    return new PlayerDistribution(3, 1);
                case 3:
                    this.gameType = "2 Humans, 2 Computers";
                    return new PlayerDistribution(2, 2);
                case 4:
                    this.gameType = "1 Human, 3 Computers";
                    return new PlayerDistribution(1, 3);
                default:
                    return new PlayerDistribution(4, 0);
            }
        }
    }

    /**
     * Choose computer difficulty
     */
    private ComputerDifficulty chooseDifficulty() {
        System.out.println("\nChoose computer difficulty:");
        System.out.println("1. Easy");
        System.out.println("2. Medium");
        System.out.println("3. Hard");

        int choice = getValidChoice(1, 3, "Enter choice (1-3): ");

        switch (choice) {
            case 1: return ComputerDifficulty.EASY;
            case 2: return ComputerDifficulty.MEDIUM;
            case 3: return ComputerDifficulty.HARD;
            default: return ComputerDifficulty.EASY;
        }
    }


    private void createPlayersWithColors(PlayerDistribution distribution, ComputerDifficulty difficulty) {
        players = new ArrayList<>();
        PlayerColor[] availableColors = {PlayerColor.RED, PlayerColor.GREEN, PlayerColor.BLUE, PlayerColor.YELLOW};

        if (distribution.humanCount + distribution.computerCount == 2) {
            // 2 players game: each player gets 2 colors
            List<PlayerColor> player1Colors = Arrays.asList(PlayerColor.RED, PlayerColor.YELLOW);
            List<PlayerColor> player2Colors = Arrays.asList(PlayerColor.BLUE, PlayerColor.GREEN);

            // Create first player
            if (distribution.humanCount >= 1) {
                String name = inputProvider.getName("Enter name for Player 1: ");
                players.add(new HumanPlayer("player1", name, player1Colors));
            } else {
                players.add(new ComputerPlayer("player1", "Computer 1", player1Colors, difficulty));
            }

            // Create second player
            if (distribution.humanCount == 2) {
                String name = inputProvider.getName("Enter name for Player 2: ");
                players.add(new HumanPlayer("player2", name, player2Colors));
            } else {
                players.add(new ComputerPlayer("player2", "Computer 2", player2Colors, difficulty));
            }

        } else {
            // 4 players game: each player gets 1 color
            int colorIndex = 0;

            // Create human players first
            for (int i = 1; i <= distribution.humanCount; i++) {
                String name = inputProvider.getName("Enter name for Player " + i + ": ");
                Player human = new HumanPlayer("human_" + i, name, List.of(availableColors[colorIndex]));
                players.add(human);
                colorIndex++;
            }

            // Create computer players
            for (int i = 1; i <= distribution.computerCount; i++) {
                String computerName = "Computer " + i + " (" + difficulty.toString().toLowerCase() + ")";
                Player computer = new ComputerPlayer("comp_" + i, computerName, List.of(availableColors[colorIndex]), difficulty);
                players.add(computer);
                colorIndex++;
            }
        }
    }

    /**
     * Display final player configuration
     */
    private void displayPlayerSummary() {
        System.out.println("Game Type: " + gameType);
        System.out.println("Players:");
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            System.out.println((i + 1) + ". " + player.getPlayerName() +
                    " - Colors: " + player.getColors() +
                    " (" + (player.isHuman() ? "Human" : "Computer") + ")");
        }
    }

    /**
     * Helper method for input validation
     */

    private int getValidChoice(int min, int max, String prompt) {
        return inputProvider.getChoice(min, max, prompt);
    }

    // Helper classes
    private static class PlayerDistribution {
        final int humanCount;
        final int computerCount;

        PlayerDistribution(int humanCount, int computerCount) {
            this.humanCount = humanCount;
            this.computerCount = computerCount;
        }
    }

    public enum ComputerDifficulty { // Todo: move this
        EASY, MEDIUM, HARD
    }
}