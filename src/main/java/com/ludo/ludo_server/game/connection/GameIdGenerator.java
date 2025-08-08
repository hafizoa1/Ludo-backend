package com.ludo.ludo_server.game.connection;

import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class GameIdGenerator {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int ID_LENGTH = 6;
    private final Random random = new Random();

    public String generateGameId() {
        StringBuilder gameId = new StringBuilder();

        for (int i = 0; i < ID_LENGTH; i++) {
            int index = random.nextInt(CHARACTERS.length());
            gameId.append(CHARACTERS.charAt(index));
        }

        return gameId.toString();
    }
}
