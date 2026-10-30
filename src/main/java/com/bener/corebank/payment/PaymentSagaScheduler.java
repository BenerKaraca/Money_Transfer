package com.bener.corebank.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "corebank.payments.enabled", havingValue = "true")
public class PaymentSagaScheduler {
    private static final Logger log = LoggerFactory.getLogger(PaymentSagaScheduler.class);
    private final PaymentSagaOrchestrator saga;

    public PaymentSagaScheduler(PaymentSagaOrchestrator saga) { this.saga = saga; }

    @Scheduled(fixedDelayString = "${corebank.payments.poll-ms:3000}")
    public void process() {
        try { saga.processPending(); }
        catch (Exception exception) { log.warn("Payment reconciliation will retry", exception); }
    }
}
