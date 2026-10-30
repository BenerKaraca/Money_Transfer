package com.bener.corebank.fx;

import com.bener.corebank.shared.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class FxQuoteClient {
    private final RestClient client;

    public FxQuoteClient(RestClient.Builder builder, @Value("${corebank.fx-rates-url}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.client = builder.requestFactory(factory).baseUrl(baseUrl).build();
    }

    public Quote get(UUID id) {
        try {
            return client.get().uri("/api/fx/quotes/{id}", id).retrieve().body(Quote.class);
        } catch (RestClientException exception) {
            throw new DomainException("FX_QUOTE_UNAVAILABLE", "Döviz teklifi doğrulanamadı; yeni teklif alıp tekrar deneyin");
        }
    }

    public record Quote(UUID id, String sourceCurrency, String targetCurrency, BigDecimal sourceAmount,
                        BigDecimal targetAmount, BigDecimal rate, LocalDate rateDate, Instant expiresAt) {}
}
