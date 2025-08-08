package com.ludo.ludo_server.game.input;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Slf4j
public class WebSocketInput implements InputProvider {

    private final WebSocketSession session;

    private String lastReceivedMessage;

    public WebSocketInput(WebSocketSession session) {
        this.session = session;
    }

    @Override
    public int getChoice(int min, int max, String prompt) {
        try {
            // Send prompt to client
            session.sendMessage(new TextMessage(prompt));

            // Wait for response (you'll set this from your handler)
            // For now, parse from lastReceivedMessage
            int choice = Integer.parseInt(lastReceivedMessage.trim());

            if (choice >= min && choice <= max) {
                return choice;
            } else {
                session.sendMessage(new TextMessage("Invalid choice! Please enter " + min + "-" + max));
                return getChoice(min, max, prompt); // Retry
            }
        } catch (Exception e) {

            log.error(e.getMessage());
            return getChoice(min, max, prompt);
        }

    }

    @Override
    public String getName(String prompt) {
        try {
            session.sendMessage(new TextMessage(prompt));
            return lastReceivedMessage.trim();
        } catch (Exception e) {
            return "Player";
        }
    }

    @Override
    public void sendMessage(String message) {
        try {
            session.sendMessage(new TextMessage(message));
        } catch (Exception e) {
            System.err.println("Failed to send message: " + e.getMessage());
        }
    }

    @Override
    public void waitForInput(String prompt) {
        try {
            session.sendMessage(new TextMessage(prompt + " (send any message to continue)"));
            // Implementation will wait for next message via setLastMessage()
        } catch (Exception e) {
            System.err.println("Failed to send prompt: " + e.getMessage());
        }
    }

    // Called from your WebSocket handler
    public void setLastMessage(String message) {
        this.lastReceivedMessage = message;
    }

}
