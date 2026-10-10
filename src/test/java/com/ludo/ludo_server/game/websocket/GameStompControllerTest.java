package com.ludo.ludo_server.game.websocket;

import com.ludo.ludo_server.game.connection.GameManager;
import com.ludo.ludo_server.game.websocket.controller.GameResponse;
import com.ludo.ludo_server.game.websocket.controller.ResponseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the two paths in this controller that handle their own exceptions
 * directly (rollDice, and the shared handleException fallback itself) -
 * these can be exercised with a plain unit test since nothing here depends
 * on Spring actually dispatching anything.
 *
 * NOT covered here, and NOT coverable this way: whether Spring's own
 * @MessageExceptionHandler routing actually invokes handleException() when
 * createGame/joinGame/leaveGame/getGameState/makeChoice throw. That routing
 * only happens through Spring's real STOMP message-dispatch machinery,
 * which no test in this codebase currently spins up. Calling those methods
 * directly here would just let the exception propagate straight out of the
 * test, the same way it would from any plain Java method call - it would
 * NOT exercise the annotation-based routing at all. A true end-to-end check
 * of that would need a heavier @SpringBootTest with a real STOMP client.
 */
class GameStompControllerTest {

    private GameManager gameManager;
    private SimpMessagingTemplate messagingTemplate;
    private GameStompController controller;
    private Principal principal;

    @BeforeEach
    void setUp() {
        gameManager = mock(GameManager.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        controller = new GameStompController();

        // Field injection (@Autowired fields, no constructor) - this is the
        // standard way to get mocks into a field-injected class without
        // starting a Spring context.
        ReflectionTestUtils.setField(controller, "gameManager", gameManager);
        ReflectionTestUtils.setField(controller, "messagingTemplate", messagingTemplate);

        principal = mock(Principal.class);
        when(principal.getName()).thenReturn("session1");
    }

    @Test
    @DisplayName("rollDice logs the exception and sends a ROLL_ERROR response instead of swallowing it silently")
    void rollDice_unexpectedException_logsAndSendsRollError() {
        when(gameManager.handleDiceRoll("session1")).thenThrow(new RuntimeException("boom"));

        // Should not propagate - rollDice has its own try/catch.
        controller.rollDice(principal);

        verify(messagingTemplate).convertAndSendToUser(eq("session1"), eq("/queue/response"),
                argThat(response -> {
                    GameResponse r = (GameResponse) response;
                    return r.getType() == ResponseType.ROLL_ERROR && !r.isSuccess();
                }));
    }

    @Test
    @DisplayName("rollDice sends the normal response through when nothing throws")
    void rollDice_normalFlow_sendsResponse() {
        GameResponse rollResponse = GameResponse.success(ResponseType.DICE_ROLL_RECEIVED, "Processing dice roll...");
        when(gameManager.handleDiceRoll("session1")).thenReturn(rollResponse);

        controller.rollDice(principal);

        verify(messagingTemplate).convertAndSendToUser("session1", "/queue/response", rollResponse);
    }

    @Test
    @DisplayName("handleException returns a SERVER_ERROR response and never throws, regardless of the exception")
    void handleException_returnsServerErrorResponse() {
        GameResponse response = controller.handleException(new NullPointerException("something was null"), principal);

        assertThat(response.getType()).isEqualTo(ResponseType.SERVER_ERROR);
        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("handleException still returns a response even with no principal (anonymous/unauthenticated case)")
    void handleException_nullPrincipal_stillReturnsResponse() {
        GameResponse response = controller.handleException(new RuntimeException("boom"), null);

        assertThat(response.getType()).isEqualTo(ResponseType.SERVER_ERROR);
        assertThat(response.isSuccess()).isFalse();
    }
}
