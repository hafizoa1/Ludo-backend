package com.ludo.ludo_server.game.state;

import com.ludo.ludo_server.board.Position;
import com.ludo.ludo_server.piece.MoveOption;
import lombok.Data;

import java.util.List;

/**
 * Wire-format view of a single offered choice (a move or a capture target) -
 * the "index" is exactly the 1-based value a client should send back via
 * /app/game.choice to pick this option.
 */
@Data
public class MoveOptionState {

    private int index;
    private String pieceId;
    private int diceValue;
    private String moveType;
    private Position targetPosition;
    private String description;
    // Computed by looking ahead before the move is chosen (see
    // MoveExecutor.findCapturablePiecesAt), not after - lets the client show
    // "this move captures something" as a preview instead of only finding
    // out once it's too late to pick differently.
    private boolean willCapture;
    private List<String> capturablePieceIds;

    public MoveOptionState(int index, MoveOption move) {
        this.index = index;
        this.pieceId = move.getPiece().getId();
        this.diceValue = move.getDiceValue();
        this.moveType = move.getMoveType().name();
        this.targetPosition = move.getTargetPosition();
        this.description = move.generateDescription();
        this.willCapture = move.isWillCapture();
        this.capturablePieceIds = move.getCapturablePieceIds();
    }
}
