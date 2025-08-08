package com.ludo.ludo_server.game.input;

public interface InputProvider {


    int getChoice(int min, int max, String prompt);

    String getName(String prompt);

    void sendMessage(String message);

    void waitForInput(String prompt);
}