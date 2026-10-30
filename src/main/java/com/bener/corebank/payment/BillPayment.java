package com.bener.corebank.payment;

import com.bener.corebank.account.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bill_payments")
public class BillPayment {
    @Id private UUID id;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 20)
    private String scenario;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BillPayment() {}

    public BillPayment(String key, Account account, BigDecimal amount, String scenario) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = key;
        this.account = account;
        this.amount = amount;
        this.scenario = scenario;
        this.status = "PENDING";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void status(String next) { this.status = next; this.updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getAccountId() { return account.getId(); }
    public String getIdempotencyKey() { return idempotencyKey; }
    public BigDecimal getAmount() { return amount; }
    public String getScenario() { return scenario; }
    public String getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
}
