package com.bener.corebank.payment;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Component
@Profile("!test")
public class HttpPaymentGateway implements PaymentGateway {
    private final RestClient client;
    private final CircuitBreakerFactory<?, ?> breakers;

    public HttpPaymentGateway(RestClient.Builder builder, CircuitBreakerFactory<?, ?> breakers,
                              @Value("${corebank.payment-provider-url:http://localhost:8081}") String url) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        this.client = builder.baseUrl(url).requestFactory(requestFactory).build();
        this.breakers = breakers;
    }

    @Override
    public Outcome submit(UUID paymentId, BigDecimal amount, String scenario) {
        return breakers.create("paymentProvider").run(() -> {
            ProviderResponse response = client.post().uri("/provider/payments")
                    .body(new ProviderRequest(paymentId, amount, scenario))
                    .retrieve().body(ProviderResponse.class);
            return response == null ? Outcome.UNKNOWN : Outcome.valueOf(response.status());
        }, error -> Outcome.UNKNOWN);
    }

    private record ProviderRequest(UUID paymentId, BigDecimal amount, String scenario) {}
    private record ProviderResponse(String status) {}
}
