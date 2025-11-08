package com.ludo.ludo_server.game.websocket.controller;

import com.ludo.ludo_server.game.state.GameState;
import lombok.Data;

@Data
public class GameResponse {
    private boolean success;
    private ResponseType type;
    private String message; 
    private GameState data;
    private String error;

    public static GameResponse success(ResponseType type, String message, GameState data) {
        GameResponse response = new GameResponse();
        response.success = true;
        response.type = type;
        response.message = message;
        response.data = data;
        return response;
    }

    public static GameResponse success(ResponseType type, String message) {
        GameResponse response = new GameResponse();
        response.success = true;
        response.type = type;
        response.message = message;
        response.data = null;
        return response;
    }

    public static GameResponse error(ResponseType type, String message) {
        GameResponse response = new GameResponse();
        response.success = false;
        response.type = type;
        response.error = message;
        return response;
    }
}

