package com.bener.provider;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderPaymentService {
    private final ProviderPaymentRepository repository;

    public ProviderPaymentService(ProviderPaymentRepository repository) { this.repository = repository; }

    @Transactional
    public Result submit(UUID id, BigDecimal amount, String scenario) {
        if (id == null || amount == null || amount.signum() <= 0 || scenario == null
                || !Set.of("APPROVE", "REJECT", "TIMEOUT_ONCE").contains(scenario)) {
            throw new IllegalArgumentException("Invalid payment request");
        }
        ProviderPayment existing = repository.findById(id).orElse(null);
        if (existing != null) {
            if (existing.getAmount().compareTo(amount) != 0 || !existing.getScenario().equals(scenario)) {
                throw new IllegalArgumentException("Idempotency payload mismatch");
            }
            return new Result(existing.getStatus(), false);
        }
        String status = scenario.equals("REJECT") ? "REJECTED" : "ACCEPTED";
        repository.save(new ProviderPayment(id, amount, scenario, status));
        return new Result(status, scenario.equals("TIMEOUT_ONCE"));
    }

    @Transactional(readOnly = true)
    public String status(UUID id) {
        return repository.findById(id).map(ProviderPayment::getStatus).orElse("UNKNOWN");
    }

    public record Result(String status, boolean simulateLostReply) {}
}
