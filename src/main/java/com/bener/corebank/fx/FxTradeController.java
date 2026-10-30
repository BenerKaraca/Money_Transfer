package com.bener.corebank.fx;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exchanges")
public class FxTradeController {
    private final FxTradeService service;
    private final com.bener.corebank.security.BankAuthorization authorization;
    public FxTradeController(FxTradeService service, com.bener.corebank.security.BankAuthorization authorization) { this.service = service; this.authorization = authorization; }

    @PostMapping
    public ResponseEntity<FxTradeService.Trade> execute(@RequestHeader("Idempotency-Key") String key,
                                                         @Valid @RequestBody TradeRequest request) {
        authorization.requireOwnAccount(request.sourceAccountId());
        authorization.requireOwnAccount(request.destinationAccountId());
        return ResponseEntity.status(201).body(service.execute(request.sourceAccountId(),
                request.destinationAccountId(), request.quoteId(), key));
    }

    @GetMapping
    public List<FxTradeService.Trade> list(@RequestParam UUID accountId) { authorization.requireOwnAccount(accountId); return service.list(accountId); }

    public record TradeRequest(@NotNull UUID sourceAccountId, @NotNull UUID destinationAccountId,
                               @NotNull UUID quoteId) {}
}
