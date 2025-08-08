package com.ludo.ludo_server.game.state;

import com.ludo.ludo_server.player.Player;
import com.ludo.ludo_server.player.PlayerColor;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
public class PlayerState {

    private String playerId;
    private String playerName;
    private List<String> colors;
    private boolean isHuman;
    private int homePiecesCount;
    private int activePiecesCount;
    private int finishedPiecesCount;
    private boolean hasWon;

    public PlayerState(Player player) {
        this.playerId = player.getPlayerId();
        this.playerName = player.getPlayerName();
        this.colors = player.getColors().stream()
                .map(PlayerColor::getFullName)
                .collect(Collectors.toList());
        this.isHuman = player.isHuman();
        this.homePiecesCount = player.getHomePieces().size();
        this.activePiecesCount = player.getActivePieces().size();
        this.finishedPiecesCount = player.getFinishedPieces().size();
        this.hasWon = player.hasWon();
    }

}
