package com.ludo.ludo_server;

import com.ludo.ludo_server.game.Game;
import com.ludo.ludo_server.player.HumanPlayer;
import com.ludo.ludo_server.player.Player;
import com.ludo.ludo_server.player.PlayerColor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

@SpringBootApplication
public class LudoServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LudoServerApplication.class, args);
	}

}

//spring web, webscket and lombok
//Topic, websocket and disconnect listener
