package com.ludo.ludo_server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Collections;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    @Value("${allowed.origins}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for topics
        config.enableSimpleBroker("/topic", "/queue");

        // Set prefix for app destinations
        config.setApplicationDestinationPrefixes("/app");

        // Optional: Set user-specific prefix
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Parse allowed origins from properties file
        String[] origins = allowedOrigins.split(",");

        // Log the CORS origins being used
        logger.info("CORS ALLOWED ORIGINS: {}", allowedOrigins);

        // Main endpoint with SockJS fallback
        registry.addEndpoint("/game")
                .setAllowedOriginPatterns(origins)
                .withSockJS();

        // Raw WebSocket endpoint (for testing/advanced clients)
        registry.addEndpoint("/game-ws")
                .setAllowedOriginPatterns(origins);
    }

    /**
     * This method adds an interceptor to the client inbound channel.
     * The interceptor's job is to read the 'login' header from the STOMP CONNECT frame
     * and set the user's Principal for the session.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String userLogin = accessor.getFirstNativeHeader("login");
                    if (userLogin == null || userLogin.isBlank()) {
                        throw new MessageDeliveryException("Missing required 'login' header on CONNECT");
                    }
                    // Create a Principal object from the user login
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userLogin, null, Collections.emptyList());
                    accessor.setUser(auth);
                }
                return message;
            }
        });
    }

}