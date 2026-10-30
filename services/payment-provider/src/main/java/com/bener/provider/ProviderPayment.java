package com.bener.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_payments")
public class ProviderPayment {
    @Id private UUID id;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 20) private String scenario;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ProviderPayment() {}
    public ProviderPayment(UUID id, BigDecimal amount, String scenario, String status) {
        this.id = id;
        this.amount = amount;
        this.scenario = scenario;
        this.status = status;
        this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public String getScenario() { return scenario; }
    public String getStatus() { return status; }
}
