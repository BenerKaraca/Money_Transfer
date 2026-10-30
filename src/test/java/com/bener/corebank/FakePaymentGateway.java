package com.bener.corebank;

import com.bener.corebank.payment.PaymentGateway;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class FakePaymentGateway implements PaymentGateway {
    private final ConcurrentHashMap<UUID, Integer> attempts = new ConcurrentHashMap<>();

    @Override
    public Outcome submit(UUID paymentId, BigDecimal amount, String scenario) {
        int attempt = attempts.merge(paymentId, 1, Integer::sum);
        return switch (scenario) {
            case "APPROVE" -> Outcome.ACCEPTED;
            case "REJECT" -> Outcome.REJECTED;
            case "TIMEOUT_ONCE" -> attempt == 1 ? Outcome.UNKNOWN : Outcome.ACCEPTED;
            default -> throw new IllegalArgumentException("Unknown scenario");
        };
    }
}
