package com.bener.corebank.payment;

import java.math.BigDecimal;
import java.util.UUID;

/** Adapter port: the core does not depend on the provider's HTTP details. */
public interface PaymentGateway {
    Outcome submit(UUID paymentId, BigDecimal amount, String scenario);

    enum Outcome { ACCEPTED, REJECTED, UNKNOWN }
}
