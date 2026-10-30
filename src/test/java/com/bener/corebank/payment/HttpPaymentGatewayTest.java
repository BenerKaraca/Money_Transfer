package com.bener.corebank.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.web.client.RestClient;

class HttpPaymentGatewayTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void mapsAcceptedEmptyAndFailedProviderReplies() throws Exception {
        AtomicInteger mode = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/provider/payments", exchange -> {
            int status = mode.get() == 1 ? 204 : mode.get() == 2 ? 500 : 200;
            byte[] body = status == 200 ? "{\"status\":\"ACCEPTED\"}".getBytes(StandardCharsets.UTF_8)
                    : status == 500 ? "{\"error\":\"unavailable\"}".getBytes(StandardCharsets.UTF_8) : new byte[0];
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, status == 204 ? -1 : body.length);
            if (body.length > 0) exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            CircuitBreakerFactory factory = mock(CircuitBreakerFactory.class);
            CircuitBreaker breaker = mock(CircuitBreaker.class);
            when(factory.create("paymentProvider")).thenReturn(breaker);
            when(breaker.run(any(Supplier.class), any(Function.class))).thenAnswer(invocation -> {
                Supplier<?> call = invocation.getArgument(0);
                Function<Throwable, ?> fallback = invocation.getArgument(1);
                try { return call.get(); }
                catch (Throwable failure) { return fallback.apply(failure); }
            });
            HttpPaymentGateway gateway = new HttpPaymentGateway(RestClient.builder(), factory,
                    "http://127.0.0.1:" + server.getAddress().getPort());
            UUID id = UUID.randomUUID();
            assertThat(gateway.submit(id, BigDecimal.ONE, "APPROVE")).isEqualTo(PaymentGateway.Outcome.ACCEPTED);
            mode.set(1);
            assertThat(gateway.submit(id, BigDecimal.ONE, "APPROVE")).isEqualTo(PaymentGateway.Outcome.UNKNOWN);
            mode.set(2);
            assertThat(gateway.submit(id, BigDecimal.ONE, "APPROVE")).isEqualTo(PaymentGateway.Outcome.UNKNOWN);
        } finally {
            server.stop(0);
        }
    }
}
