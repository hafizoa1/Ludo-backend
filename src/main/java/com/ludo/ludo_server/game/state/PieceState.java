package com.ludo.ludo_server.game.state;

import com.ludo.ludo_server.board.Position;
import com.ludo.ludo_server.piece.Piece;
import lombok.Data;

@Data
public class PieceState {

    private String id;
    private String color;
    private int number;
    private Position position;
    private int pathPosition;
    private boolean isAtHome;
    private boolean isFinished;
    private boolean isInSafeZone;

    public PieceState(Piece piece) {
        this.id = piece.getId();
        this.color = piece.getColor().name();
        this.number = piece.getNumber();
        this.position = (piece.getBoardPosition());
        this.pathPosition = piece.getPathPosition();
        this.isAtHome = piece.isAtHome();
        this.isFinished = piece.isFinished();
        this.isInSafeZone = piece.isInSafeZone();
    }

}
