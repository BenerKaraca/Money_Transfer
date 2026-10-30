package com.bener.corebank.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Customer() {}

    public Customer(String fullName) {
        this.id = UUID.randomUUID();
        this.fullName = fullName.trim();
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
}
