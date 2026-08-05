package com.ludo.ludo_server.piece;


import com.ludo.ludo_server.board.Position;
import com.ludo.ludo_server.player.Player;
import lombok.Getter;

public class MoveOption {

    @Getter
    private final Player player;
    @Getter
    private final Piece piece;
    @Getter
    private final int diceValue;
    @Getter
    private final MoveType moveType;
    @Getter
    private final Position targetPosition;
    private final String description;

    // Constructor for normal moves and bringing out pieces
    public MoveOption(Player player, Piece piece, int diceValue, MoveType moveType) {
        this.piece = piece;
        this.player = player;
        this.diceValue = diceValue;
        this.moveType = moveType;
        this.targetPosition = calculateTargetPosition();
        this.description = generateDescription();
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
            case CAPTURE:
                desc.append(": Capture this piece");
                break;
            default:
                break;
        }

        if (targetPosition != null) {
            desc.append(" to ").append(targetPosition.toString());
        }

        return desc.toString();
    }
}
