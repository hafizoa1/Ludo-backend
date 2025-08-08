package com.ludo.ludo_server.game.input;

import java.util.InputMismatchException;
import java.util.Scanner;

public class ScannerInput implements InputProvider {
    private final Scanner scanner;

    public ScannerInput(Scanner scanner) {
        this.scanner = scanner;
    }

    @Override
    public int getChoice(int min, int max, String prompt) {
        int choice = -1;
        boolean valid = false;

        while (!valid) {
            try {
                System.out.print(prompt);
                choice = scanner.nextInt();
                scanner.nextLine(); // Consume newline

                if (choice >= min && choice <= max) {
                    valid = true;
                } else {
                    System.out.println("Invalid choice! Please enter a number between " + min + " and " + max);
                }
            } catch (InputMismatchException e) {
                System.out.println("Please enter a valid number:");
                scanner.nextLine(); // Clear invalid input
            }
        }
        return choice;
    }


    @Override
    public String getName(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    @Override
    public void sendMessage(String message) {
        System.out.println(message);
    }

    @Override
    public void waitForInput(String prompt) {
        System.out.println(prompt);
        scanner.nextLine(); // Wait for any input
    }

}
