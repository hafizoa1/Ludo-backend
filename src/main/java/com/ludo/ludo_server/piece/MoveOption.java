package com.ludo.ludo_server.piece;


import com.ludo.ludo_server.board.Position;
import com.ludo.ludo_server.player.Player;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
    @Getter
    private final boolean willCapture;
    @Getter
    private final List<String> capturablePieceIds;

    // Constructor for moves that can never capture (BRING_OUT, FINISH), or
    // anywhere capture data isn't being computed by the caller.
    public MoveOption(Player player, Piece piece, int diceValue, MoveType moveType) {
        this(player, piece, diceValue, moveType, Collections.emptyList());
    }

    // capturablePieces: every enemy piece this move would land on and could
    // capture - computed by the caller (MoveExecutor, which holds the Board
    // reference needed to look ahead), not by MoveOption itself. Empty if
    // this move doesn't capture anything.
    //
    // Stored as ids only, not full Piece references - a piece's id already
    // encodes its color (e.g. "R2" = Red piece 2), which is enough for
    // generateDescription(), and keeps this class a simple data holder.
    public MoveOption(Player player, Piece piece, int diceValue, MoveType moveType, List<Piece> capturablePieces) {
        this.piece = piece;
        this.player = player;
        this.diceValue = diceValue;
        this.moveType = moveType;
        this.targetPosition = calculateTargetPosition();
        this.capturablePieceIds = capturablePieces.stream().map(Piece::getId).collect(Collectors.toList());
        this.willCapture = !this.capturablePieceIds.isEmpty();
    }

    // Calculate where the piece would end up after this move
    private Position calculateTargetPosition() {
        switch (moveType) {
            case BRING_OUT:
                // Calculate the starting position for this piece's color
                return piece.getStartingPosition();
            case NORMAL:
            case COMBINED:
                return piece.calculateNewPosition(diceValue);
            default:
                return piece.getBoardPosition();
        }
    }

    // Generate a human-readable description of this move. Not cached -
    // called repeatedly from Game's turn loop and from MoveOptionState, so
    // it always reflects current field values rather than a stale snapshot
    // taken at construction time.
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
                // MoveExecutor.choosePieceToCapture builds one MoveOption per
                // enemy piece it's offering as a capture target - piece here
                // is always the one doing the capturing (never the captured
                // piece - see MoveExecutor for the bug this used to be), and
                // the target is in capturablePieceIds (exactly one entry for
                // this move type).
                desc.append(": Capture ");
                desc.append(capturablePieceIds.isEmpty() ? "piece" : capturablePieceIds.get(0));
                break;
            default:
                break;
        }

        if (willCapture && moveType != MoveType.CAPTURE) {
            desc.append(" (captures ").append(String.join(", ", capturablePieceIds)).append(")");
        }

        if (targetPosition != null) {
            desc.append(" to ").append(targetPosition.toString());
        }

        return desc.toString();
    }
}
