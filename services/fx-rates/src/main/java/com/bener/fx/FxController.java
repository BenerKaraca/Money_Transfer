package com.bener.fx;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fx")
public class FxController {
    private final FxQuoteService service;
    public FxController(FxQuoteService service) { this.service = service; }

    @GetMapping("/rates")
    public List<ReferenceRateProvider.Rate> rates() { return service.rates(); }

    @PostMapping("/quotes")
    public ResponseEntity<FxQuoteService.Quote> quote(@Valid @RequestBody QuoteRequest request) {
        return ResponseEntity.status(201).body(service.quote(request.sourceCurrency(), request.targetCurrency(), request.amount()));
    }

    @GetMapping("/quotes/{id}")
    public FxQuoteService.Quote find(@PathVariable UUID id) { return service.find(id); }

    public record QuoteRequest(@NotBlank String sourceCurrency, @NotBlank String targetCurrency,
                               @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount) {}
}
