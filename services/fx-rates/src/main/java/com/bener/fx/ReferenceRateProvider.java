package com.bener.fx;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ReferenceRateProvider {
    private final RestClient client;

    public ReferenceRateProvider(RestClient.Builder builder, @Value("${fx.provider-url}") String providerUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.client = builder.requestFactory(factory).baseUrl(providerUrl).build();
    }

    public Rate rate(String base, String quote) {
        return client.get().uri("/v2/rate/{base}/{quote}", base.toLowerCase(), quote.toLowerCase())
                .retrieve().body(Rate.class);
    }

    public record Rate(LocalDate date, String base, String quote, BigDecimal rate) {}
}
