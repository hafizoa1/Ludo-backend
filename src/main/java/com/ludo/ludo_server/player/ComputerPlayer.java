package com.ludo.ludo_server.player;



import com.ludo.ludo_server.piece.Piece;

import java.util.List;
import java.util.Random;

/**
 * Computer player with simple AI logic
 */
public class ComputerPlayer extends Player {

    private final Random random;

    public ComputerPlayer(String playerId, String playerName, List<PlayerColor> colors, ComputerDifficulty difficulty) {
        super(playerId, playerName, colors);
        this.random = new Random();
    }

    @Override
    public boolean isHuman() {
        return false;
    }

}
