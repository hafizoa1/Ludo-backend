package com.ludo.ludo_server.game.websocket;

import com.ludo.ludo_server.board.Board;
import com.ludo.ludo_server.board.Position;
import com.ludo.ludo_server.game.Game;
import com.ludo.ludo_server.game.connection.GameManager;
import com.ludo.ludo_server.game.connection.GameRoom;
import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import com.ludo.ludo_server.piece.MovementPath;
import com.ludo.ludo_server.piece.Piece;
import com.ludo.ludo_server.player.Player;
import com.ludo.ludo_server.player.PlayerColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;

import java.security.Principal;
import java.util.Map;

import static com.ludo.ludo_server.game.websocket.controller.ResponseType.GAME_MESSAGE;

/**
 * Manual test helpers for reaching specific board scenarios (e.g. a
 * multi-capture stack) without playing dozens of real turns to get there.
 * @Profile("dev") means this bean doesn't exist outside the dev profile -
 * not a runtime check that could be left on by accident, the endpoints
 * simply aren't registered at all.
 */
@Controller
@Profile("dev")
public class GameDebugController {

    private static final Logger logger = LoggerFactory.getLogger(GameDebugController.class);

    @Autowired
    private GameManager gameManager;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/game.debug.setDice")
    public void setDice(@RequestBody Map<String, Object> request) {
        Game game = requireGame((String) request.get("gameId"));
        if (game == null) return;

        int die1 = (Integer) request.get("die1");
        int die2 = (Integer) request.get("die2");
        game.getDice().setValues(die1, die2);

        logger.info("[DEBUG] Dice set to {}, {}", die1, die2);
    }

    @MessageMapping("/game.debug.stagePiece")
    public void stagePiece(@RequestBody Map<String, Object> request) {
        Game game = requireGame((String) request.get("gameId"));
        if (game == null) return;

        String pieceId = (String) request.get("pieceId");
        Piece piece = findPiece(game, pieceId);
        if (piece == null) {
            logger.warn("[DEBUG] No piece {} in this game", pieceId);
            return;
        }

        int pathPosition = (Integer) request.get("pathPosition");
        Integer row = (Integer) request.get("row");
        Integer col = (Integer) request.get("col");

        Board board = game.getBoard();
        board.removePiece(piece);
        piece.setPathPosition(pathPosition);

        if (row != null && col != null) {
            piece.setBoardPosition(new Position(row, col));
            board.placePiece(piece, row, col);
        } else {
            Position pos = piece.getBoardPosition();
            board.placePiece(piece, pos.row(), pos.col());
        }

        logger.info("[DEBUG] Staged {} at pathPosition={}, square={}",
                pieceId, pathPosition, piece.getBoardPosition());
    }

    /**
     * Read-only lookup: where does a given color's path position actually
     * sit on the board? Lets a human construct a scenario (e.g. "stage two
     * enemies on the square red would land on") without tracing
     * MovementPath by hand.
     */
    @MessageMapping("/game.debug.whereIs")
    public void whereIs(@RequestBody Map<String, Object> request, Principal user) {
        String colorName = (String) request.get("color");
        int pathPosition = (Integer) request.get("pathPosition");
        PlayerColor color = PlayerColor.valueOf(colorName.toUpperCase());

        Position position = MovementPath.getCoordinateAt(color, pathPosition);

        messagingTemplate.convertAndSendToUser(user.getName(), "/queue/response",
                GameResponse.success(GAME_MESSAGE,
                        colorName + " at pathPosition " + pathPosition + " is at " + position));
    }

    private Game requireGame(String gameId) {
        GameRoom room = gameManager.getGameRoom(gameId);
        if (room == null || room.getGame() == null) {
            logger.warn("[DEBUG] No active game: {}", gameId);
            return null;
        }
        return room.getGame();
    }

    private Piece findPiece(Game game, String pieceId) {
        for (Player player : game.getPlayers()) {
            for (Piece piece : player.getPieces()) {
                if (piece.getId().equals(pieceId)) {
                    return piece;
                }
            }
        }
        return null;
    }
}
