package com.ludo.ludo_server.game;

import com.ludo.ludo_server.board.Board;
import com.ludo.ludo_server.board.Position;
import com.ludo.ludo_server.game.input.InputProvider;
import com.ludo.ludo_server.piece.MoveOption;
import com.ludo.ludo_server.piece.MoveType;
import com.ludo.ludo_server.piece.Piece;
import com.ludo.ludo_server.player.HumanPlayer;
import com.ludo.ludo_server.player.Player;
import com.ludo.ludo_server.player.PlayerColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for Game's current behavior, written before pulling move
 * calculation and capture handling out into their own class, so the
 * extraction can be checked against these instead of against intuition.
 *
 * Weighted toward captures deliberately - that's where this ruleset's actual
 * complexity (and its deviation from real Ludo: a capture instantly finishes
 * the capturing piece) lives, not in simple movement.
 */
class GameTest {

    private Player redPlayer;
    private Player bluePlayer;
    private Game game;
    private Board board;
    private TestInputProvider inputProvider;

    @BeforeEach
    void setUp() {
        redPlayer = new HumanPlayer("p1", "Red Player", List.of(PlayerColor.RED));
        bluePlayer = new HumanPlayer("p2", "Blue Player", List.of(PlayerColor.BLUE));
        inputProvider = new TestInputProvider();
        game = new Game(List.of(redPlayer, bluePlayer), inputProvider, null, "test-game");
        board = game.getBoard();
    }

    /** Test double for InputProvider - always answers with a queued (or default) choice. */
    private static class TestInputProvider implements InputProvider {
        private final Deque<Integer> choices = new ArrayDeque<>();
        private int defaultChoice = 1;

        void willChoose(int choice) {
            choices.addLast(choice);
        }

        @Override
        public int getChoice(int min, int max, String prompt) {
            return choices.isEmpty() ? defaultChoice : choices.removeFirst();
        }

        @Override
        public String getName(String prompt) {
            return "Test Player";
        }

        @Override
        public void sendMessage(String message) {
            // no-op
        }

        @Override
        public void waitForInput(String prompt) {
            // no-op
        }
    }

    /** Stage a piece as active (on the board, not home/finished) at a given path position. */
    private void stageActive(Piece piece, int pathPosition) {
        board.removePiece(piece);
        piece.setPathPosition(pathPosition);
        Position pos = piece.getBoardPosition();
        board.placePiece(piece, pos.row(), pos.col());
    }

    /** Stage a piece as active at an exact board square, overriding wherever its path position would normally put it. */
    private void stageActiveAt(Piece piece, Position exactSquare, int pathPosition) {
        board.removePiece(piece);
        piece.setPathPosition(pathPosition);
        board.placePiece(piece, exactSquare.row(), exactSquare.col());
    }

    // =========================================================================
    // MOVE MECHANICS (via the public movePiece entry point)
    // =========================================================================

    @Nested
    @DisplayName("move mechanics")
    class MoveMechanics {

        @Test
        @DisplayName("a piece at home doesn't move without rolling a 6")
        void homePiece_needsSix() {
            Piece piece = redPlayer.getPieces()[0];

            game.getMoveExecutor().movePiece(piece, redPlayer, 3);

            assertThat(piece.isAtHome()).isTrue();
        }

        @Test
        @DisplayName("a piece at home exits with a 6")
        void homePiece_exitsWithSix() {
            Piece piece = redPlayer.getPieces()[0];

            game.getMoveExecutor().movePiece(piece, redPlayer, 6);

            assertThat(piece.isAtHome()).isFalse();
            assertThat(piece.getPathPosition()).isEqualTo(0);
        }

        @Test
        @DisplayName("an active piece advances by the dice value")
        void activePiece_advancesNormally() {
            Piece piece = redPlayer.getPieces()[0];
            stageActive(piece, 10);

            game.getMoveExecutor().movePiece(piece, redPlayer, 4);

            assertThat(piece.getPathPosition()).isEqualTo(14);
            assertThat(piece.isFinished()).isFalse();
        }

        @Test
        @DisplayName("a move that would overshoot the finish line leaves the piece unchanged")
        void overshootingMove_isRejected() {
            Piece piece = redPlayer.getPieces()[0];
            stageActive(piece, 54); // 2 away from FINISHED_POSITION (56)

            game.getMoveExecutor().movePiece(piece, redPlayer, 6); // 54 + 6 = 60, overshoots

            assertThat(piece.getPathPosition()).isEqualTo(54);
            assertThat(piece.isFinished()).isFalse();
        }

        @Test
        @DisplayName("landing exactly on the finish position finishes the piece via movement")
        void exactFinish_finishesThePiece() {
            Piece piece = redPlayer.getPieces()[0];
            stageActive(piece, 50);

            game.getMoveExecutor().movePiece(piece, redPlayer, 6); // 50 + 6 = 56 = FINISHED_POSITION exactly

            assertThat(piece.isFinished()).isTrue();
            assertThat(piece.getBoardPosition()).isEqualTo(new Position(7, 7));
        }
    }

    // =========================================================================
    // CAPTURES - the weighted-heavier group
    // =========================================================================

    @Nested
    @DisplayName("captures")
    class Captures {

        @Test
        @DisplayName("landing on an empty square triggers no capture")
        void emptySquare_noCapture() {
            Piece mover = redPlayer.getPieces()[0];
            stageActive(mover, 10);

            game.getMoveExecutor().movePiece(mover, redPlayer, 3);

            assertThat(board.getPiecesAt(mover.getBoardPosition().row(), mover.getBoardPosition().col()))
                    .containsExactly(mover);
        }

        @Test
        @DisplayName("a single capturable piece is captured automatically, no choice needed")
        void singleCapturablePiece_autoCaptured() {
            Piece mover = redPlayer.getPieces()[0];
            stageActive(mover, 10);
            Position landing = mover.calculateNewPosition(3);

            Piece target = bluePlayer.getPieces()[0];
            stageActiveAt(target, landing, 10);

            game.getMoveExecutor().movePiece(mover, redPlayer, 3);

            assertThat(target.isAtHome()).isTrue();
        }

        @Test
        @DisplayName("a capture instantly finishes the capturing piece (this ruleset's custom rule)")
        void capture_finishesTheCapturingPiece() {
            Piece mover = redPlayer.getPieces()[0];
            stageActive(mover, 10);
            Position landing = mover.calculateNewPosition(3);

            Piece target = bluePlayer.getPieces()[0];
            stageActiveAt(target, landing, 10);

            game.getMoveExecutor().movePiece(mover, redPlayer, 3);

            assertThat(mover.isFinished()).isTrue();
            assertThat(mover.getBoardPosition()).isEqualTo(new Position(7, 7));
        }

        @Test
        @DisplayName("a captured piece is sent back to its own correct home slot")
        void capturedPiece_sentToOwnHomeSlot() {
            Piece mover = redPlayer.getPieces()[0];
            stageActive(mover, 10);
            Position landing = mover.calculateNewPosition(3);

            Piece target = bluePlayer.getPieces()[1]; // B2 - not the first home slot, to prove it's not hardcoded
            stageActiveAt(target, landing, 10);

            game.getMoveExecutor().movePiece(mover, redPlayer, 3);

            Position expectedHome = Board.initialHomeYardCoords.get(PlayerColor.BLUE)[1];
            assertThat(target.getBoardPosition()).isEqualTo(expectedHome);
        }

        @Test
        @DisplayName("multiple capturable pieces at the same square trigger a player choice")
        void multipleCapturablePieces_triggersChoice() {
            Piece mover = redPlayer.getPieces()[0];
            stageActive(mover, 10);
            Position landing = mover.calculateNewPosition(3);

            Piece target1 = bluePlayer.getPieces()[0];
            Piece target2 = bluePlayer.getPieces()[1];
            stageActiveAt(target1, landing, 10);
            stageActiveAt(target2, landing, 12);

            inputProvider.willChoose(2); // pick the second offered piece

            game.getMoveExecutor().movePiece(mover, redPlayer, 3);

            assertThat(target2.isAtHome()).isTrue();
            assertThat(target1.isAtHome()).isFalse();
            assertThat(target1.getBoardPosition()).isEqualTo(landing);
        }

        @Test
        @DisplayName("multiple capturable pieces are exposed as structured CAPTURE options before the choice resolves")
        void multipleCapturablePieces_exposedAsStructuredOptions() {
            Piece mover = redPlayer.getPieces()[0];
            stageActive(mover, 10);
            Position landing = mover.calculateNewPosition(3);

            Piece target1 = bluePlayer.getPieces()[0];
            Piece target2 = bluePlayer.getPieces()[1];
            stageActiveAt(target1, landing, 10);
            stageActiveAt(target2, landing, 12);

            inputProvider.willChoose(1);

            game.getMoveExecutor().movePiece(mover, redPlayer, 3);

            // Nothing after the capture resolves clears currentOptions in this
            // direct-call scenario, so this is exactly what was offered right
            // before the choice was made.
            List<MoveOption> offered = game.getCurrentOptions();
            assertThat(offered).hasSize(2);
            assertThat(offered).allMatch(option -> option.getMoveType() == MoveType.CAPTURE);
            assertThat(offered.stream().map(option -> option.getPiece().getId()))
                    .containsExactly(target1.getId(), target2.getId());
        }

        @Test
        @DisplayName("landing on your own color's piece does not capture it")
        void ownColorPiece_notCaptured() {
            Piece mover = redPlayer.getPieces()[0];
            stageActive(mover, 10);
            Position landing = mover.calculateNewPosition(3);

            Piece ownOtherPiece = redPlayer.getPieces()[1];
            stageActiveAt(ownOtherPiece, landing, 10);

            game.getMoveExecutor().movePiece(mover, redPlayer, 3);

            assertThat(ownOtherPiece.isAtHome()).isFalse();
            assertThat(ownOtherPiece.getBoardPosition()).isEqualTo(landing);
            assertThat(mover.isFinished()).isFalse(); // no capture -> no instant-finish
            assertThat(mover.getPathPosition()).isEqualTo(13); // just a normal advance, 10 + 3
        }

        @Test
        @DisplayName("a finished piece cannot be captured")
        void finishedPiece_cannotBeCaptured() {
            Piece mover = redPlayer.getPieces()[0];
            stageActive(mover, 10);
            Position landing = mover.calculateNewPosition(3);

            Piece alreadyFinished = bluePlayer.getPieces()[0];
            // Staged at an arbitrary track square while finished, purely to test
            // the canCapture guard in isolation - not a normally reachable board state.
            stageActiveAt(alreadyFinished, landing, Piece.FINISHED_POSITION);

            game.getMoveExecutor().movePiece(mover, redPlayer, 3);

            assertThat(mover.isFinished()).isFalse(); // no capture happened
            assertThat(mover.getPathPosition()).isEqualTo(13);
        }
    }

    // =========================================================================
    // TURN SEQUENCING (via the public continueAfterDiceRoll entry point)
    // =========================================================================

    @Nested
    @DisplayName("turn sequencing")
    class TurnSequencing {

        @Test
        @DisplayName("a normal roll advances to the next player")
        void normalRoll_advancesToNextPlayer() {
            game.getDice().setValues(3, 4); // no 6, no active pieces -> no moves possible

            game.continueAfterDiceRoll();

            assertThat(game.getCurrentPlayer()).isEqualTo(bluePlayer);
        }

        @Test
        @DisplayName("double-6 keeps the same player for another turn")
        void doubleSix_samePlayerAgain() {
            Player startingPlayer = game.getCurrentPlayer();
            game.getDice().setValues(6, 6);

            game.continueAfterDiceRoll();

            assertThat(game.getCurrentPlayer()).isEqualTo(startingPlayer);
        }

        @Test
        @DisplayName("both dice values get used across two moves in one turn")
        void bothDiceValues_getUsedInOneTurn() {
            game.getDice().setValues(6, 3); // 6 brings a piece out, 3 should then move it further

            game.continueAfterDiceRoll();

            Piece broughtOut = redPlayer.getPieces()[0];
            assertThat(broughtOut.getPathPosition()).isEqualTo(3); // 0 (bring-out) + 3
        }

        @Test
        @DisplayName("winning ends the turn without advancing to the next player")
        void winningMove_endsWithoutAdvancing() {
            Piece[] pieces = redPlayer.getPieces();
            stageActive(pieces[0], Piece.FINISHED_POSITION);
            stageActive(pieces[1], Piece.FINISHED_POSITION);
            stageActive(pieces[2], Piece.FINISHED_POSITION);
            stageActive(pieces[3], 54);
            game.getDice().setValues(2, 3); // 54 + 2 = 56 = finish; not a double, so advance would normally happen

            game.continueAfterDiceRoll();

            assertThat(game.getWinner()).isEqualTo(redPlayer.getPlayerName());
            assertThat(game.getCurrentPlayer()).isEqualTo(redPlayer); // did not advance to blue
        }
    }
}
