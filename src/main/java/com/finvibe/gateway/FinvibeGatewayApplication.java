package com.finvibe.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Finvibe Gateway 애플리케이션의 진입점이다.
 */
@SpringBootApplication
public class FinvibeGatewayApplication {

	/**
	 * Spring Boot 애플리케이션을 시작한다.
	 *
	 * @param args 실행 인자
	 */
	public static void main(String[] args) {
		SpringApplication.run(FinvibeGatewayApplication.class, args);
	}

}
