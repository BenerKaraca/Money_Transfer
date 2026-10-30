package com.bener.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class FxServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);
    private final ReferenceRateProvider provider = mock(ReferenceRateProvider.class);
    private final FxQuoteService service = new FxQuoteService(provider, clock);

    @Test void quotesAndRatesUseProviderValuesAndExpire() {
        var rate = new ReferenceRateProvider.Rate(LocalDate.parse("2026-10-27"), "EUR", "TRY", new BigDecimal("55.639"));
        when(provider.rate("EUR", "TRY")).thenReturn(rate);
        when(provider.rate("USD", "TRY")).thenReturn(rate);
        when(provider.rate("EUR", "USD")).thenReturn(rate);
        assertThat(service.rates()).hasSize(3);
        var quote = service.quote("EUR", "TRY", new BigDecimal("10.00"));
        assertThat(quote.targetAmount()).isEqualByComparingTo("556.39");
        assertThat(quote.rateDate()).isEqualTo(LocalDate.parse("2026-10-27"));
        assertThat(quote.expiresAt()).isEqualTo(Instant.parse("2026-10-28T12:02:00Z"));
        assertThat(service.find(quote.id())).isEqualTo(quote);
        assertThatThrownBy(() -> service.find(UUID.randomUUID())).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        AtomicReference<Instant> instant = new AtomicReference<>(Instant.parse("2026-10-28T12:00:00Z"));
        Clock movingClock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant.get(), zone); }
            @Override public Instant instant() { return instant.get(); }
        };
        var expired = new FxQuoteService(provider, movingClock);
        when(provider.rate("EUR", "TRY")).thenReturn(rate);
        var oldQuote = expired.quote("EUR", "TRY", BigDecimal.ONE);
        instant.set(Instant.parse("2026-10-28T12:03:00Z"));
        assertThatThrownBy(() -> expired.find(oldQuote.id())).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.GONE));
    }

    @Test void invalidPairsAmountsAndProviderResponsesAreRejected() {
        assertThatThrownBy(() -> service.quote("ABC", "TRY", BigDecimal.ONE)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.quote("EUR", "ABC", BigDecimal.ONE)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.quote("EUR", "EUR", BigDecimal.ONE)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.quote("EUR", "TRY", null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.quote("EUR", "TRY", BigDecimal.ZERO)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.quote("EUR", "TRY", new BigDecimal("1.001"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.quote("EUR", "TRY", new BigDecimal("123456789012345678.00"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.quote("EUR", "TRY", BigDecimal.ONE)).isInstanceOf(ResponseStatusException.class);
        when(provider.rate("EUR", "TRY")).thenReturn(new ReferenceRateProvider.Rate(LocalDate.now(clock), "EUR", "TRY", null));
        assertThatThrownBy(() -> service.quote("EUR", "TRY", BigDecimal.ONE)).isInstanceOf(ResponseStatusException.class);
        when(provider.rate("EUR", "TRY")).thenReturn(new ReferenceRateProvider.Rate(LocalDate.now(clock), "EUR", "TRY", BigDecimal.ZERO));
        assertThatThrownBy(() -> service.quote("EUR", "TRY", BigDecimal.ONE)).isInstanceOf(ResponseStatusException.class);
        when(provider.rate("EUR", "TRY")).thenReturn(new ReferenceRateProvider.Rate(LocalDate.now(clock), "EUR", "TRY", new BigDecimal("0.000001")));
        assertThatThrownBy(() -> service.quote("EUR", "TRY", new BigDecimal("0.01"))).isInstanceOf(ResponseStatusException.class);
    }

    @Test void controllerMapsQuoteEndpoints() {
        var controller = new FxController(service);
        when(provider.rate("EUR", "TRY")).thenReturn(new ReferenceRateProvider.Rate(LocalDate.now(clock), "EUR", "TRY", BigDecimal.TEN));
        when(provider.rate("USD", "TRY")).thenReturn(new ReferenceRateProvider.Rate(LocalDate.now(clock), "USD", "TRY", BigDecimal.TEN));
        when(provider.rate("EUR", "USD")).thenReturn(new ReferenceRateProvider.Rate(LocalDate.now(clock), "EUR", "USD", BigDecimal.ONE));
        assertThat(controller.rates()).hasSize(3);
        var request = new FxController.QuoteRequest("EUR", "TRY", BigDecimal.ONE);
        var response = controller.quote(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.find(response.getBody().id())).isEqualTo(response.getBody());
    }

    @Test void providerUsesExternalHttpResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/rate/eur/try", exchange -> {
            byte[] body = "{\"date\":\"2026-10-27\",\"base\":\"EUR\",\"quote\":\"TRY\",\"rate\":55.639}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var client = new ReferenceRateProvider(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort());
            assertThat(client.rate("EUR", "TRY").rate()).isEqualByComparingTo("55.639");
        } finally { server.stop(0); }
    }

    @Test void applicationStartsAndProvidesClock() {
        try (var spring = mockStatic(SpringApplication.class)) {
            FxApplication.main(new String[0]);
            spring.verify(() -> SpringApplication.run(FxApplication.class, new String[0]));
        }
        assertThat(new FxApplication().clock()).isNotNull();
    }
}
