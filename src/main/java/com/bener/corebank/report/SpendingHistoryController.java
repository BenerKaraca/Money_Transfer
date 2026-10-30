package com.bener.corebank.report;

import com.bener.corebank.security.BankAuthorization;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spending")
public class SpendingHistoryController {
    private final SpendingHistoryService spending;
    private final BankAuthorization authorization;
    public SpendingHistoryController(SpendingHistoryService spending, BankAuthorization authorization) {
        this.spending = spending; this.authorization = authorization;
    }
    @GetMapping
    public SpendingHistoryService.History history() { return spending.history(authorization.customerId()); }
}
