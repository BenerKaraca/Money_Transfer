package com.bener.gateway;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class GatewayMainTest {
    @Test
    void mainStartsGateway() {
        try (MockedStatic<SpringApplication> spring = org.mockito.Mockito.mockStatic(SpringApplication.class)) {
            GatewayApplication.main(new String[0]);
            spring.verify(() -> SpringApplication.run(GatewayApplication.class, new String[0]));
        }
    }
}
