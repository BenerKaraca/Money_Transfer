package com.bener.corebank.report;

import com.bener.corebank.account.AccountRepository;
import com.bener.corebank.shared.DomainException;
import com.bener.corebank.transfer.TransferRepository;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountReportService {
    private final AccountRepository accounts;
    private final TransferRepository transfers;

    public AccountReportService(AccountRepository accounts, TransferRepository transfers) {
        this.accounts = accounts;
        this.transfers = transfers;
    }

    // Reporting may be up to 30 seconds stale. Balances are never cached here.
    @Cacheable(cacheNames = "transferCounts", key = "#accountId")
    @Transactional(readOnly = true)
    public long transferCount(UUID accountId) {
        if (!accounts.existsById(accountId)) {
            throw new DomainException("NOT_FOUND", "Hesap bulunamadı");
        }
        return transfers.countForAccount(accountId);
    }
}
