package com.bener.corebank.account;

import com.bener.corebank.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal reserved;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {}

    public Account(Customer customer) {
        this(customer, "TRY");
    }

    public Account(Customer customer, String currency) {
        this.id = UUID.randomUUID();
        this.customer = customer;
        this.currency = currency;
        this.balance = new BigDecimal("0.00");
        this.reserved = new BigDecimal("0.00");
        this.createdAt = Instant.now();
    }

    public void debit(BigDecimal amount) {
        if (balance.subtract(reserved).compareTo(amount) < 0) {
            throw new com.bener.corebank.shared.DomainException("INSUFFICIENT_FUNDS", "Yetersiz bakiye");
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) { balance = balance.add(amount); }
    public void reserve(BigDecimal amount) {
        if (balance.subtract(reserved).compareTo(amount) < 0) {
            throw new com.bener.corebank.shared.DomainException("INSUFFICIENT_FUNDS", "Yetersiz kullanılabilir bakiye");
        }
        reserved = reserved.add(amount);
    }
    public void release(BigDecimal amount) {
        if (reserved.compareTo(amount) < 0) {
            throw new IllegalStateException("Reservation underflow");
        }
        reserved = reserved.subtract(amount);
    }
    public UUID getId() { return id; }
    public UUID getCustomerId() { return customer.getId(); }
    public String getCustomerName() { return customer.getFullName(); }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getAvailableBalance() { return balance.subtract(reserved); }
}
