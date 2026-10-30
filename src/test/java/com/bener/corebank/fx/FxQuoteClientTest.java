package com.bener.corebank.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bener.corebank.shared.DomainException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class FxQuoteClientTest {
    @Test void readsQuoteAndMapsProviderErrors() throws Exception {
        UUID id = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/fx/quotes/", exchange -> {
            boolean found = exchange.getRequestURI().getPath().endsWith(id.toString());
            byte[] body = found
                    ? ("{\"id\":\"" + id + "\",\"sourceCurrency\":\"TRY\",\"targetCurrency\":\"EUR\","
                            + "\"sourceAmount\":100.00,\"targetAmount\":2.00,\"rate\":0.02,"
                            + "\"rateDate\":\"2026-10-27\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}")
                            .getBytes(StandardCharsets.UTF_8)
                    : "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(found ? 200 : 404, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var client = new FxQuoteClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort());
            assertThat(client.get(id).sourceAmount()).isEqualByComparingTo("100.00");
            assertThatThrownBy(() -> client.get(UUID.randomUUID())).isInstanceOf(DomainException.class)
                    .hasMessageContaining("doğrulanamadı");
        } finally { server.stop(0); }
    }
}
