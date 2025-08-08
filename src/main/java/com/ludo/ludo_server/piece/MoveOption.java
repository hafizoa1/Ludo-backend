package com.ludo.ludo_server.piece;


import com.ludo.ludo_server.board.Position;

public class MoveOption {
    private Piece piece;
    private int diceValue;
    private MoveType moveType;
    private Position targetPosition;
    private String description;

    // Constructor for normal moves and bringing out pieces
    public MoveOption(Piece piece, int diceValue, MoveType moveType) {
        this.piece = piece;
        this.diceValue = diceValue;
        this.moveType = moveType;
        this.targetPosition = calculateTargetPosition();
        this.description = generateDescription();
    }

    public Piece getPiece() {
        return piece;
    }

    public int getDiceValue() {
        return diceValue;
    }

    // Calculate where the piece would end up after this move
    private Position calculateTargetPosition() {
        switch (moveType) {
            case BRING_OUT:
                // Calculate the starting position for this piece's color
                return piece.getStartingPosition();
            case NORMAL:
                return piece.calculateNewPosition(diceValue);
            case COMBINED:
                // Calculate new position based on current position + dice value
                return piece.calculateNewPosition(diceValue);
            default:
                return piece.getBoardPosition();
        }
    }

    // Generate a human-readable description of this move
    public String generateDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append(piece.getColor().toString()).append(" piece ").append(piece.getNumber());

        switch (moveType) {
            case BRING_OUT:
                desc.append(": Bring out of home (using 6)");
                break;
            case NORMAL:
                desc.append(": Move ").append(diceValue).append(" spaces");
                break;
            case COMBINED:
                desc.append(": Move ").append(diceValue).append(" spaces (combined dice)");
                break;
            case FINISH:
                desc.append(": Finish the game!");
                break;
        }

        if (targetPosition != null) {
            desc.append(" to ").append(targetPosition.toString());
        }

        return desc.toString();
    }
}
