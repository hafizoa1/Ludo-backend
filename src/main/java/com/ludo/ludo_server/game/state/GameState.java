package com.ludo.ludo_server.game.state;

import com.ludo.ludo_server.board.Board;
import com.ludo.ludo_server.board.Position;
import com.ludo.ludo_server.game.Game;
import com.ludo.ludo_server.piece.MoveOption;
import com.ludo.ludo_server.piece.Piece;
import com.ludo.ludo_server.player.Player;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class GameState {
    private DiceState dice;
    private String currentPlayerName;
    private String currentPlayerId;
    private List<PlayerState> players;
    private List<PieceState> pieces;
    private List<MoveOptionState> availableMoves;
    private boolean gameOver;
    private String winner;
    private String gameStatus;

    // Constructor that extracts state from your existing Game
    public GameState(Game game) {
        this.dice = new DiceState(game.getDice());

        // Current player info
        Player currentPlayer = game.getCurrentPlayer();
        this.currentPlayerName = currentPlayer.getPlayerName();
        this.currentPlayerId = currentPlayer.getPlayerId();

        // All players
        this.players = game.getPlayers().stream()
                .map(PlayerState::new)
                .collect(Collectors.toList());

        // All pieces from board
        this.pieces = extractPiecesFromBoard(game.getBoard());

        // Game status
        this.gameOver = game.isGameOver();
        this.gameStatus = gameOver ? "FINISHED" : "IN_PROGRESS";

        // Winner if game is over
        if (gameOver) {
            this.winner = game.getWinner();
        }

        // Whatever choices (moves or capture targets) are currently offered
        // to the current player, indexed to match the /app/game.choice reply.
        this.availableMoves = buildAvailableMoves(game.getCurrentOptions());
    }

    private List<MoveOptionState> buildAvailableMoves(List<MoveOption> currentOptions) {
        List<MoveOptionState> states = new ArrayList<>();
        for (int i = 0; i < currentOptions.size(); i++) {
            states.add(new MoveOptionState(i + 1, currentOptions.get(i)));
        }
        return states;
    }

    // Extract all pieces from the board
    private List<PieceState> extractPiecesFromBoard(Board board) {
        List<PieceState> allPieces = new ArrayList<>();

        // Get all pieces from all positions on the board
        Map<Position, List<Piece>> allPositions = board.getAllPiecePositions();

        for (List<Piece> piecesAtPosition : allPositions.values()) {
            for (Piece piece : piecesAtPosition) {
                allPieces.add(new PieceState(piece));
            }
        }

        return allPieces;
    }

}