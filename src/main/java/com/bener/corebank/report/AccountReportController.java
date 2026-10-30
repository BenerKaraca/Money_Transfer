package com.bener.corebank.report;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class AccountReportController {
    private final AccountReportService reports;
    private final com.bener.corebank.security.BankAuthorization authorization;

    public AccountReportController(AccountReportService reports, com.bener.corebank.security.BankAuthorization authorization) { this.reports = reports; this.authorization = authorization; }

    @GetMapping("/accounts/{accountId}/transfer-count")
    public TransferCountResponse transferCount(@PathVariable UUID accountId) {
        authorization.requireOwnAccount(accountId);
        return new TransferCountResponse(accountId, reports.transferCount(accountId));
    }

    public record TransferCountResponse(UUID accountId, long count) {}
}
