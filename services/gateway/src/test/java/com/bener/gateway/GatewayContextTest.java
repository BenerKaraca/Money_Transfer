package com.bener.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

@SpringBootTest
class GatewayContextTest {
    @Autowired RouteDefinitionLocator routes;

    @Test
    void routesCoreBankApi() {
        assertThat(routes.getRouteDefinitions().map(route -> route.getId()).collectList().block())
                .contains("corebank-api");
    }
}
