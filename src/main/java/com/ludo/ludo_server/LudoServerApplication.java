package com.ludo.ludo_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;


@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
public class LudoServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LudoServerApplication.class, args);
	}

}

//spring web, webscket and lombok
//Topic, websocket and disconnect listener
//TODO: clients are not receiving messages - after choices are made communication is not being made back to clients
