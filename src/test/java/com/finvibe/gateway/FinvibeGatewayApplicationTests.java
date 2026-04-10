package com.finvibe.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest(properties = {
        "finvibe.gateway.services.market-url=ws://localhost:18090",
        "finvibe.gateway.services.was-url=http://localhost:18081"
})
class FinvibeGatewayApplicationTests {

    @Autowired
    private RouteLocator routeLocator;

	@Test
	void contextLoads() {
		assertThat(routeLocator).isNotNull();
	}

	@Test
	void exposesConfiguredRoutesInOrder() {
		List<Route> routes = routeLocator.getRoutes().collectList().block();

		assertThat(routes)
				.isNotNull()
				.extracting(Route::getId)
				.containsExactly("market-ws-service", "was-service");
		assertThat(routes)
				.extracting(route -> route.getUri().toString())
				.containsExactly("ws://localhost:18090", "http://localhost:18081");
		assertThat(routes)
				.extracting(Route::getOrder)
				.containsExactly(-1, 0);
	}

}
