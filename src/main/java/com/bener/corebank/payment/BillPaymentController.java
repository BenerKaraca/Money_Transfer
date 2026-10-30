package com.bener.corebank.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bill-payments")
public class BillPaymentController {
    private final BillPaymentService service;
    private final PaymentSagaOrchestrator saga;
    private final com.bener.corebank.security.BankAuthorization authorization;

    public BillPaymentController(BillPaymentService service, PaymentSagaOrchestrator saga,
                                 com.bener.corebank.security.BankAuthorization authorization) {
        this.service = service;
        this.saga = saga;
        this.authorization = authorization;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> start(@RequestHeader("Idempotency-Key") String key,
                                                  @Valid @RequestBody PaymentRequest request) {
        authorization.requireOwnAccount(request.accountId());
        BillPayment payment = service.start(request.accountId(), request.amount(), key, request.scenario());
        return ResponseEntity.status(202).body(response(payment));
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id) { BillPayment payment = service.get(id); authorization.requireOwnAccount(payment.getAccountId()); return response(payment); }

    @GetMapping
    public List<PaymentResponse> list(@RequestParam UUID accountId) {
        authorization.requireOwnAccount(accountId);
        return service.list(accountId).stream().map(BillPaymentController::response).toList();
    }

    @PostMapping("/{id}/reconcile")
    public PaymentResponse reconcile(@PathVariable UUID id) { BillPayment payment = service.get(id); authorization.requireOwnAccount(payment.getAccountId()); return response(saga.reconcile(id)); }

    private static PaymentResponse response(BillPayment payment) {
        return new PaymentResponse(payment.getId(), payment.getAccountId(), payment.getAmount(), payment.getStatus());
    }

    public record PaymentRequest(@NotNull UUID accountId, @NotNull @DecimalMin("0.01")
                                 @Digits(integer = 17, fraction = 2) BigDecimal amount, String scenario) {}
    public record PaymentResponse(UUID id, UUID accountId, BigDecimal amount, String status) {}
}
