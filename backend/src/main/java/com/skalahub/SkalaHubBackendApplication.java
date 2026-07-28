// SKALA Hub 백엔드 애플리케이션 진입점
package com.skalahub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SkalaHubBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SkalaHubBackendApplication.class, args);
	}

}
