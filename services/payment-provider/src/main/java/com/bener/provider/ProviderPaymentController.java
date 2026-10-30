package com.bener.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/provider/payments")
public class ProviderPaymentController {
    private final ProviderPaymentService service;

    public ProviderPaymentController(ProviderPaymentService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<PaymentResponse> submit(@Valid @RequestBody PaymentRequest request) throws InterruptedException {
        ProviderPaymentService.Result result = service.submit(request.paymentId(), request.amount(), request.scenario());
        if (result.simulateLostReply()) Thread.sleep(4000); // Commit happened before the delayed response.
        return ResponseEntity.ok(new PaymentResponse(result.status()));
    }

    @GetMapping("/{id}")
    public PaymentResponse status(@PathVariable UUID id) { return new PaymentResponse(service.status(id)); }

    public record PaymentRequest(@NotNull UUID paymentId, @NotNull @DecimalMin("0.01") BigDecimal amount,
                                 @NotNull String scenario) {}
    public record PaymentResponse(String status) {}
}
