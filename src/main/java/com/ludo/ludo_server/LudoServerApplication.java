package com.ludo.ludo_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;


// ManagementWebSecurityAutoConfiguration is excluded alongside
// SecurityAutoConfiguration for the same reason: this app has no
// HttpSecurity bean (no SecurityFilterChain is configured anywhere), so
// Actuator's own attempt to secure management endpoints fails to start
// the app otherwise. Actuator exposure is restricted at the properties
// level instead (management.endpoints.web.exposure.include=health).
@SpringBootApplication(exclude = {SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
public class LudoServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LudoServerApplication.class, args);
	}

}

