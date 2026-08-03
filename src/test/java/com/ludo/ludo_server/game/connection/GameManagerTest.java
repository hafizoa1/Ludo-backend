package com.ludo.ludo_server.game.connection;

import com.ludo.ludo_server.game.Game;
import com.ludo.ludo_server.game.input.InputProvider;
import com.ludo.ludo_server.game.input.PlayerTimeoutException;
import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import com.ludo.ludo_server.piece.Piece;
import com.ludo.ludo_server.player.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ludo.ludo_server.game.websocket.controller.ResponseType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Regression tests for GameManager's current behavior, written before splitting
 * it apart, so the split can be checked against these instead of against intuition.
 */
class GameManagerTest {

    private static final Pattern GAME_ID_PATTERN = Pattern.compile("Game (\\w+) created");

    private GameIdGenerator gameIdGenerator;
    private SessionMapper sessionMapper;
    private StompGameEventBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        gameIdGenerator = new GameIdGenerator();
        sessionMapper = new SessionMapper();
        broadcaster = mock(StompGameEventBroadcaster.class);
    }

    private GameManager newGameManager(long disconnectTimeoutSeconds) {
        return new GameManager(gameIdGenerator, sessionMapper, broadcaster, disconnectTimeoutSeconds);
    }

    private String extractGameId(GameResponse response) {
        Matcher m = GAME_ID_PATTERN.matcher(response.getMessage());
        assertThat(m.find()).as("expected a game id in: %s", response.getMessage()).isTrue();
        return m.group(1);
    }

    private static void awaitUntil(BooleanSupplier condition, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertThat(condition.getAsBoolean()).as("condition not met within %s", timeout).isTrue();
    }

    /** Test double for InputProvider - lets us control a turn's choice without waiting on real timeouts. */
    private static class TestInputProvider implements InputProvider {
        private final IntSupplier choiceSupplier;

        TestInputProvider(IntSupplier choiceSupplier) {
            this.choiceSupplier = choiceSupplier;
        }

        @Override
        public int getChoice(int min, int max, String prompt) {
            return choiceSupplier.getAsInt();
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

    // =========================================================================
    // ROOM / LOBBY LIFECYCLE
    // =========================================================================

    @Nested
    @DisplayName("room lifecycle: create / join / leave")
    class RoomLifecycle {

        @Test
        @DisplayName("createGame registers a room and reports waiting for a second player")
        void createGame_registersRoom() {
            GameManager gameManager = newGameManager(30);

            GameResponse response = gameManager.createGame("session1", "player1id");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getType()).isEqualTo(GAME_CREATED);
            String gameId = extractGameId(response);
            assertThat(gameManager.getGameRoom(gameId)).isNotNull();
        }

        @Test
        @DisplayName("joinGame on an unknown game id fails")
        void joinGame_unknownGame_fails() {
            GameManager gameManager = newGameManager(30);

            GameResponse response = gameManager.joinGame("session2", "NOSUCHGAME", "player2id");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getType()).isEqualTo(GAME_NOT_FOUND);
        }

        @Test
        @DisplayName("a second distinct player joining starts the game")
        void joinGame_secondPlayer_startsGame() {
            GameManager gameManager = newGameManager(30);
            String gameId = extractGameId(gameManager.createGame("session1", "player1id"));

            GameResponse response = gameManager.joinGame("session2", gameId, "player2id");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getType()).isEqualTo(JOINED_GAME);
            GameRoom gameRoom = gameManager.getGameRoom(gameId);
            assertThat(gameRoom.getGame()).isNotNull();
            assertThat(gameRoom.getStatus()).isEqualTo(GameRoom.GameRoomStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("a third distinct player cannot join a full room")
        void joinGame_fullRoom_fails() {
            GameManager gameManager = newGameManager(30);
            String gameId = extractGameId(gameManager.createGame("session1", "player1id"));
            gameManager.joinGame("session2", gameId, "player2id");

            GameResponse response = gameManager.joinGame("session3", gameId, "player3id");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getType()).isEqualTo(GAME_FULL);
        }

        @Test
        @DisplayName("the same playerId rejoining is treated as a reconnect, not a new player")
        void joinGame_samePlayerId_reconnects() {
            GameManager gameManager = newGameManager(30);
            String gameId = extractGameId(gameManager.createGame("session1", "player1id"));
            gameManager.joinGame("session2", gameId, "player2id");

            GameResponse response = gameManager.joinGame("session1-new", gameId, "player1id");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getType()).isEqualTo(JOINED_GAME);
            assertThat(response.getMessage()).containsIgnoringCase("reconnect");
            // still only 2 sessions in the room, not 3
            assertThat(gameManager.getGameRoom(gameId).getSessionIds()).hasSize(2);
        }

        @Test
        @DisplayName("leaveGame when not in any game fails")
        void leaveGame_notInGame_fails() {
            GameManager gameManager = newGameManager(30);

            GameResponse response = gameManager.leaveGame("nobody");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getType()).isEqualTo(NO_GAME);
        }

        @Test
        @DisplayName("the last player leaving removes the room entirely")
        void leaveGame_lastPlayer_removesRoom() {
            GameManager gameManager = newGameManager(30);
            String gameId = extractGameId(gameManager.createGame("session1", "player1id"));

            gameManager.leaveGame("session1");

            assertThat(gameManager.getGameRoom(gameId)).isNull();
        }

        @Test
        @DisplayName("one of two players leaving keeps the room alive")
        void leaveGame_onePlayerOfTwo_keepsRoom() {
            GameManager gameManager = newGameManager(30);
            String gameId = extractGameId(gameManager.createGame("session1", "player1id"));
            gameManager.joinGame("session2", gameId, "player2id");

            gameManager.leaveGame("session1");

            assertThat(gameManager.getGameRoom(gameId)).isNotNull();
        }
    }

    // =========================================================================
    // TURN ACTIONS
    // =========================================================================

    @Nested
    @DisplayName("turn actions: roll / choice / state")
    class TurnActions {

        @Test
        @DisplayName("rolling with no active game fails")
        void handleDiceRoll_noGame_fails() {
            GameManager gameManager = newGameManager(30);

            GameResponse response = gameManager.handleDiceRoll("nobody");

            assertThat(response.getType()).isEqualTo(NO_GAME);
        }

        @Test
        @DisplayName("rolling out of turn is rejected")
        void handleDiceRoll_wrongTurn_fails() {
            GameManager gameManager = newGameManager(30);
            String gameId = extractGameId(gameManager.createGame("session1", "player1id"));
            gameManager.joinGame("session2", gameId, "player2id");
            GameRoom gameRoom = gameManager.getGameRoom(gameId);
            Player currentPlayer = gameRoom.getGame().getCurrentPlayer();
            String otherSessionId = gameRoom.getSessionIdForPlayer(currentPlayer).equals("session1") ? "session2" : "session1";

            GameResponse response = gameManager.handleDiceRoll(otherSessionId);

            assertThat(response.getType()).isEqualTo(NOT_YOUR_TURN);
        }

        @Test
        @DisplayName("rolling on your turn broadcasts the result and starts processing")
        void handleDiceRoll_validTurn_broadcastsAndProcesses() {
            GameManager gameManager = newGameManager(30);
            String gameId = extractGameId(gameManager.createGame("session1", "player1id"));
            gameManager.joinGame("session2", gameId, "player2id");
            GameRoom gameRoom = gameManager.getGameRoom(gameId);
            String currentSessionId = gameRoom.getSessionIdForPlayer(gameRoom.getGame().getCurrentPlayer());

            GameResponse response = gameManager.handleDiceRoll(currentSessionId);

            assertThat(response.getType()).isEqualTo(DICE_ROLL_RECEIVED);
            verify(broadcaster, timeout(1000)).broadcastToGame(eq(gameId), argThat(r -> r.getType() == DICE_ROLLED));
        }

        @Test
        @DisplayName("making a choice with nothing pending is rejected")
        void handlePlayerChoice_noPendingRequest_fails() {
            GameManager gameManager = newGameManager(30);
            String gameId = extractGameId(gameManager.createGame("session1", "player1id"));
            gameManager.joinGame("session2", gameId, "player2id");

            GameResponse response = gameManager.handlePlayerChoice("session1", 1);

            assertThat(response.getType()).isEqualTo(NO_PENDING_CHOICE);
        }

        @Test
        @DisplayName("game state with no active game fails")
        void getGameState_noGame_fails() {
            GameManager gameManager = newGameManager(30);

            GameResponse response = gameManager.getGameState("nobody");

            assertThat(response.getType()).isEqualTo(NO_GAME);
        }

        @Test
        @DisplayName("game state with an active game returns it")
        void getGameState_activeGame_returnsState() {
            GameManager gameManager = newGameManager(30);
            String gameId = extractGameId(gameManager.createGame("session1", "player1id"));
            gameManager.joinGame("session2", gameId, "player2id");

            GameResponse response = gameManager.getGameState("session1");

            assertThat(response.getType()).isEqualTo(GAME_STATE);
            assertThat(response.getData()).isNotNull();
        }
    }

    // =========================================================================
    // GAME-ENDING CLEANUP - the part that actually protects the refactor
    // =========================================================================

    @Nested
    @DisplayName("game-ending cleanup: win / AFK-timeout / disconnect-timeout / reconnect")
    class GameEndingCleanup {

        private String gameId;
        private GameRoom gameRoom;
        private Game game;
        private Player currentPlayer;
        private String currentSessionId;
        private String otherSessionId;
        // the external, client-supplied playerId SessionMapper tracks for reconnection -
        // NOT the same value as the domain Player's own playerId field.
        private String currentExternalPlayerId;

        private void startTwoPlayerGame(GameManager gameManager) {
            gameId = extractGameId(gameManager.createGame("session1", "player1id"));
            gameManager.joinGame("session2", gameId, "player2id");
            gameRoom = gameManager.getGameRoom(gameId);
            game = gameRoom.getGame();
            currentPlayer = game.getCurrentPlayer();
            currentSessionId = gameRoom.getSessionIdForPlayer(currentPlayer);
            otherSessionId = currentSessionId.equals("session1") ? "session2" : "session1";
            currentExternalPlayerId = currentSessionId.equals("session1") ? "player1id" : "player2id";
        }

        @Test
        @DisplayName("a real win removes the room and both sessions, and broadcasts GAME_ENDED")
        void win_cleansUpEverything() {
            GameManager gameManager = newGameManager(30);
            startTwoPlayerGame(gameManager);

            // Rig the current player's pieces as already finished, so this roll
            // finds zero available moves and the turn ends straight into a win -
            // no need to touch the real 30s choice-timeout at all.
            game.setInputProvider(new TestInputProvider(
                    () -> { throw new AssertionError("getChoice should not be called - no moves should be available"); }));
            for (Piece piece : currentPlayer.getPieces()) {
                piece.setPathPosition(Piece.FINISHED_POSITION);
            }

            gameManager.handleDiceRoll(currentSessionId);

            awaitUntil(() -> gameManager.getGameRoom(gameId) == null, Duration.ofSeconds(2));
            assertThat(sessionMapper.findPlayerSessionBySessionId(currentSessionId)).isNull();
            assertThat(sessionMapper.findPlayerSessionBySessionId(otherSessionId)).isNull();

            ArgumentCaptor<GameResponse> captor = ArgumentCaptor.forClass(GameResponse.class);
            verify(broadcaster, atLeastOnce()).broadcastToGame(eq(gameId), captor.capture());
            assertThat(captor.getAllValues()).anyMatch(r -> r.getType() == GAME_ENDED);
        }

        @Test
        @DisplayName("an unresponsive player during their turn forfeits, and cleans up the same way")
        void afkDuringTurn_cleansUpEverything() {
            GameManager gameManager = newGameManager(30);
            startTwoPlayerGame(gameManager);

            // Give the current player a piece that can always move, so executePlayerTurn
            // actually asks for a choice - which our fake immediately times out on.
            currentPlayer.getPieces()[0].setPathPosition(0);
            game.setInputProvider(new TestInputProvider(() -> {
                throw new PlayerTimeoutException(currentSessionId, currentPlayer.getPlayerId(), currentPlayer.getPlayerName());
            }));

            gameManager.handleDiceRoll(currentSessionId);

            awaitUntil(() -> gameManager.getGameRoom(gameId) == null, Duration.ofSeconds(2));
            assertThat(sessionMapper.findPlayerSessionBySessionId(currentSessionId)).isNull();
            assertThat(sessionMapper.findPlayerSessionBySessionId(otherSessionId)).isNull();

            ArgumentCaptor<GameResponse> captor = ArgumentCaptor.forClass(GameResponse.class);
            verify(broadcaster, atLeastOnce()).broadcastToGame(eq(gameId), captor.capture());
            assertThat(captor.getAllValues()).anyMatch(r -> r.getType() == GAME_ENDED_TIMEOUT);
        }

        @Test
        @DisplayName("a disconnect with no reconnect ends the game after the timeout")
        void disconnectWithoutReconnect_endsGame() {
            GameManager gameManager = newGameManager(0); // fire (almost) immediately
            startTwoPlayerGame(gameManager);

            gameManager.handlePlayerDisconnect(currentSessionId);

            awaitUntil(() -> gameManager.getGameRoom(gameId) == null, Duration.ofSeconds(2));
            assertThat(sessionMapper.findPlayerSessionBySessionId(otherSessionId)).isNull();
        }

        @Test
        @DisplayName("reconnecting before the timeout cancels it - the game is not cleaned up")
        void reconnectBeforeTimeout_cancelsCleanup() throws InterruptedException {
            GameManager gameManager = newGameManager(1); // 1s window to reconnect within
            startTwoPlayerGame(gameManager);

            gameManager.handlePlayerDisconnect(currentSessionId);
            Thread.sleep(200); // well inside the 1s window
            gameManager.joinGame(currentSessionId + "-new", gameId, currentExternalPlayerId);

            // wait past the original 1s window and confirm cleanup did NOT happen
            Thread.sleep(1200);
            assertThat(gameManager.getGameRoom(gameId)).isNotNull();
        }
    }
}
