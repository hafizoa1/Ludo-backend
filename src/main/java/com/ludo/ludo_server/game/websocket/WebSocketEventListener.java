package com.ludo.ludo_server.game.websocket;

import com.ludo.ludo_server.game.connection.GameManager;
import com.ludo.ludo_server.game.connection.PlayerSession;
import com.ludo.ludo_server.game.connection.SessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * WebSocketEventListener - Listens for WebSocket connection lifecycle events
 *
 * Purpose: Detect when players connect/disconnect and handle session cleanup
 *
 * How it works:
 * - Spring automatically publishes SessionConnectEvent when a WebSocket connects
 * - Spring automatically publishes SessionDisconnectEvent when a WebSocket disconnects
 * - @EventListener tells Spring to call our methods when these events happen
 *
 * This is the MISSING PIECE that enables:
 * - Automatic disconnect detection
 * - Session cleanup
 * - Triggering reconnection timers
 * - Notifying other players
 */
@Component
public class WebSocketEventListener {

    @Autowired
    private SessionMapper sessionMapper;

    @Autowired
    private GameManager gameManager;

    /**
     * Handle WebSocket connection event
     *
     * Called automatically by Spring when a client connects
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        System.out.println("🔌 WebSocket CONNECTED: " + sessionId);

        // Note: We don't do much here yet - the actual player joining happens
        // when they send /game.create or /game.join messages
        // But we could use this for connection tracking in the future
    }

    /**
     * Handle WebSocket disconnect event
     *
     * This is called automatically by Spring when:
     * - Browser tab is closed
     * - Internet connection is lost
     * - Page is refreshed
     * - Network timeout
     * - Any other reason the WebSocket disconnects
     *
     * This is the KEY method that makes disconnect detection work!
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // IMPORTANT: Use Principal name, not WebSocket sessionId!
        // The Principal name is what we store in GameStompController (user.getName())
        String stompSessionId = (headerAccessor.getUser() != null) ? headerAccessor.getUser().getName() : headerAccessor.getSessionId();

        System.out.println("=====================================");
        System.out.println("🔌 [DISCONNECT DEBUG] WebSocket DISCONNECTED");
        System.out.println("   [DISCONNECT DEBUG] WebSocket sessionId: " + headerAccessor.getSessionId());
        System.out.println("   [DISCONNECT DEBUG] STOMP Principal: " + stompSessionId);
        System.out.println("   [DISCONNECT DEBUG] Timestamp: " + System.currentTimeMillis());

        // Try to find the player session associated with this WebSocket session
        PlayerSession playerSession = sessionMapper.findPlayerSessionBySessionId(stompSessionId);

        if (playerSession != null) {
            // Found the player - they were in a game
            String playerId = playerSession.getPlayerId();
            String gameId = playerSession.getGameId();

            System.out.println("🔌 [DISCONNECT DEBUG] Player " + playerId + " disconnected from game " + gameId);
            System.out.println("   [DISCONNECT DEBUG] Player name: " + playerSession.getDisplayName());
            System.out.println("   [DISCONNECT DEBUG] Marking player as disconnected...");

            // Mark the player as disconnected in SessionMapper
            sessionMapper.markPlayerDisconnected(stompSessionId);

            System.out.println("   [DISCONNECT DEBUG] Calling gameManager.handlePlayerDisconnect()...");
            System.out.println("   [DISCONNECT DEBUG] This will start 30-second timeout timer");

            // Notify GameManager to handle the disconnect
            // This will:
            // 1. Start the 30-second reconnection timer
            // 2. Notify the opponent that player disconnected
            // 3. Schedule game end if player doesn't reconnect
            gameManager.handlePlayerDisconnect(stompSessionId);

            System.out.println("✅ [DISCONNECT DEBUG] Disconnect handled for player: " + playerId);
        } else {
            // No player session found
            // This happens when:
            // - Client connected but never joined a game
            // - Client was just browsing the lobby
            // - Connection dropped before game join
            System.out.println("🔌 [DISCONNECT DEBUG] Disconnected session had no associated player: " + stompSessionId);

            // Still clean up from session mapper just in case
            sessionMapper.removeSession(stompSessionId);
        }

        System.out.println("=====================================");
    }
}
