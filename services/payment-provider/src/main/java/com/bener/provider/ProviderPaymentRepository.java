package com.bener.provider;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderPaymentRepository extends JpaRepository<ProviderPayment, UUID> {}
