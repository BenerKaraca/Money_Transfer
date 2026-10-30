package com.bener.corebank.payment;

import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class PaymentSagaOrchestrator {
    private final BillPaymentRepository payments;
    private final BillPaymentService service;
    private final PaymentGateway gateway;

    public PaymentSagaOrchestrator(BillPaymentRepository payments, BillPaymentService service, PaymentGateway gateway) {
        this.payments = payments;
        this.service = service;
        this.gateway = gateway;
    }

    public void processPending() {
        payments.findPendingIds(PageRequest.of(0, 20)).forEach(this::reconcile);
    }

    public BillPayment reconcile(UUID id) {
        BillPayment payment = service.get(id);
        if (payment.getStatus().equals("PENDING") || payment.getStatus().equals("UNKNOWN")) {
            PaymentGateway.Outcome outcome = gateway.submit(id, payment.getAmount(), payment.getScenario());
            service.applyOutcome(id, outcome);
        }
        return service.get(id);
    }
}
