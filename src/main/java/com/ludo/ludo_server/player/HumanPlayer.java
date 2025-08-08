package com.ludo.ludo_server.player;


import java.util.List;

/**
 * Human player implementation
 */
public class HumanPlayer extends Player {

    public HumanPlayer(String playerId, String playerName, List<PlayerColor> colors) {
        super(playerId, playerName, colors);
    }

    @Override
    public boolean isHuman() {
        return true;
    }
}
