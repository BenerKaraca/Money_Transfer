package com.bener.corebank.account;

import com.bener.corebank.customer.Customer;
import com.bener.corebank.customer.CustomerRepository;
import com.bener.corebank.shared.DomainException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final CustomerRepository customers;
    private final AccountRepository accounts;

    public AccountService(CustomerRepository customers, AccountRepository accounts) {
        this.customers = customers;
        this.accounts = accounts;
    }

    @Transactional
    public Customer createCustomer(String fullName) {
        return customers.save(new Customer(fullName));
    }

    @Transactional
    public Account openAccount(UUID customerId) {
        return openAccount(customerId, "TRY");
    }

    @Transactional
    public Account openAccount(UUID customerId, String currency) {
        if (!java.util.Set.of("TRY", "EUR", "USD").contains(currency)) {
            throw new DomainException("INVALID_CURRENCY", "Desteklenmeyen para birimi");
        }
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "Müşteri bulunamadı"));
        return accounts.save(new Account(customer, currency));
    }

    @Transactional(readOnly = true)
    public Account getAccount(UUID accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "Hesap bulunamadı"));
    }

    @Transactional(readOnly = true)
    public Page<AccountOverview> listAccounts(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException("INVALID_PAGE", "Sayfa veya boyut geçersiz");
        }
        return accounts.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(account -> new AccountOverview(account.getId(), account.getCustomerId(), account.getCustomerName(),
                        account.getCurrency(), account.getBalance(), account.getAvailableBalance()));
    }

    @Transactional(readOnly = true)
    public Page<AccountOverview> listAccounts(UUID customerId, int page, int size) {
        validatePage(page, size);
        return accounts.findAllByCustomer_IdOrderByCreatedAtDesc(customerId, PageRequest.of(page, size))
                .map(this::overview);
    }

    @Transactional(readOnly = true)
    public java.util.List<AccountOverview> payees(UUID customerId, String currency) {
        if (!java.util.Set.of("TRY", "EUR", "USD").contains(currency)) throw new DomainException("INVALID_CURRENCY", "Desteklenmeyen para birimi");
        return accounts.findAllByCurrencyAndCustomer_IdNotOrderByCreatedAtDesc(currency, customerId).stream().map(this::overview).toList();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new DomainException("INVALID_PAGE", "Sayfa veya boyut geçersiz");
    }

    private AccountOverview overview(Account account) {
        return new AccountOverview(account.getId(), account.getCustomerId(), account.getCustomerName(),
                account.getCurrency(), account.getBalance(), account.getAvailableBalance());
    }

    public record AccountOverview(UUID id, UUID customerId, String customerName, String currency, java.math.BigDecimal balance,
                                  java.math.BigDecimal availableBalance) {}
}
