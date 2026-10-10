package com.ludo.ludo_server.game.websocket;

import com.ludo.ludo_server.game.connection.GameManager;
import com.ludo.ludo_server.game.connection.PlayerSession;
import com.ludo.ludo_server.game.connection.SessionMapper;
import com.ludo.ludo_server.player.HumanPlayer;
import com.ludo.ludo_server.player.Player;
import com.ludo.ludo_server.player.PlayerColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * This is an @EventListener, not a @MessageMapping - GameStompController's
 * @MessageExceptionHandler doesn't apply to this path at all, so it needed
 * its own try/catch. These tests confirm that an unexpected exception from
 * gameManager.handlePlayerDisconnect() is actually caught here rather than
 * propagating into Spring's event-dispatch machinery.
 */
class WebSocketEventListenerTest {

    private SessionMapper sessionMapper;
    private GameManager gameManager;
    private WebSocketEventListener listener;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(SessionMapper.class);
        gameManager = mock(GameManager.class);
        listener = new WebSocketEventListener();

        ReflectionTestUtils.setField(listener, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(listener, "gameManager", gameManager);
    }

    private SessionDisconnectEvent disconnectEventFor(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        accessor.setUser(new UsernamePasswordAuthenticationToken(sessionId, null, Collections.emptyList()));

        Message<byte[]> message = MessageBuilder.withPayload(new byte[0])
                .setHeaders(accessor)
                .build();

        return new SessionDisconnectEvent(this, message, sessionId, null);
    }

    @Test
    @DisplayName("an unexpected exception from handlePlayerDisconnect is caught, not propagated")
    void disconnect_unexpectedException_isCaughtNotPropagated() {
        PlayerSession playerSession = new PlayerSession("player1id", "session1", "GAME123",
                new HumanPlayer("player1", "Player 1", List.of(PlayerColor.RED, PlayerColor.YELLOW)));

        when(sessionMapper.findPlayerSessionBySessionId("session1")).thenReturn(playerSession);
        doThrow(new RuntimeException("boom")).when(gameManager).handlePlayerDisconnect("session1");

        assertThatCode(() -> listener.handleWebSocketDisconnectListener(disconnectEventFor("session1")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a disconnect with no associated player session still cleans up, without throwing")
    void disconnect_noPlayerSession_cleansUpSafely() {
        when(sessionMapper.findPlayerSessionBySessionId("session1")).thenReturn(null);

        assertThatCode(() -> listener.handleWebSocketDisconnectListener(disconnectEventFor("session1")))
                .doesNotThrowAnyException();

        verify(sessionMapper).removeSession("session1");
    }

    @Test
    @DisplayName("normal disconnect flow still calls through to GameManager as before")
    void disconnect_normalFlow_callsGameManager() {
        PlayerSession playerSession = new PlayerSession("player1id", "session1", "GAME123",
                new HumanPlayer("player1", "Player 1", List.of(PlayerColor.RED, PlayerColor.YELLOW)));

        when(sessionMapper.findPlayerSessionBySessionId("session1")).thenReturn(playerSession);

        listener.handleWebSocketDisconnectListener(disconnectEventFor("session1"));

        verify(gameManager).handlePlayerDisconnect("session1");
    }
}
