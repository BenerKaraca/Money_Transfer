package com.bener.corebank.security;

import com.bener.corebank.account.Account;
import com.bener.corebank.account.AccountRepository;
import com.bener.corebank.shared.DomainException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class BankAuthorization {
    private final AccountRepository accounts;
    public BankAuthorization(AccountRepository accounts) { this.accounts = accounts; }
    public UUID customerId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) throw new DomainException("FORBIDDEN", "Kimliği doğrulanmış kullanıcı gerekli");
        return UUID.fromString(jwt.getToken().getClaimAsString("customer_id"));
    }
    public void requireOwnAccount(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new DomainException("NOT_FOUND", "Hesap bulunamadı"));
        if (!account.getCustomerId().equals(customerId())) throw new DomainException("FORBIDDEN", "Bu hesaba erişim yetkiniz yok");
    }
    public void requireOwnCustomer(UUID customerId) {
        if (!customerId().equals(customerId)) throw new DomainException("FORBIDDEN", "Bu müşteriye erişim yetkiniz yok");
    }
}
