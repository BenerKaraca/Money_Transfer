package com.bener.corebank.payment;

import com.bener.corebank.account.Account;
import com.bener.corebank.account.AccountRepository;
import com.bener.corebank.shared.DomainException;
import com.bener.corebank.transfer.TransferService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillPaymentService {
    public static final UUID CLEARING_ACCOUNT = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff1");

    private final BillPaymentRepository payments;
    private final AccountRepository accounts;
    private final TransferService transfers;

    public BillPaymentService(BillPaymentRepository payments, AccountRepository accounts, TransferService transfers) {
        this.payments = payments;
        this.accounts = accounts;
        this.transfers = transfers;
    }

    @Transactional
    public BillPayment start(UUID accountId, BigDecimal amount, String key, String scenario) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new DomainException("INVALID_KEY", "Geçerli bir Idempotency-Key gerekli");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.precision() - amount.scale() > 17) {
            throw new DomainException("INVALID_AMOUNT", "Geçersiz ödeme tutarı");
        }
        if (scenario == null || !java.util.Set.of("APPROVE", "REJECT", "TIMEOUT_ONCE").contains(scenario)) {
            throw new DomainException("INVALID_SCENARIO", "Geçersiz ödeme senaryosu");
        }
        BigDecimal normalized = amount.setScale(2);
        BillPayment existing = payments.findByIdempotencyKey(key).orElse(null);
        if (existing != null) return matching(existing, accountId, normalized, scenario);

        Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "Hesap bulunamadı"));
        if (!account.getCurrency().equals("TRY")) {
            throw new DomainException("CURRENCY_MISMATCH", "Fatura ödemesi yalnızca TRY hesabından yapılabilir");
        }
        existing = payments.findByIdempotencyKey(key).orElse(null);
        if (existing != null) return matching(existing, accountId, normalized, scenario);
        account.reserve(normalized);
        return payments.save(new BillPayment(key, account, normalized, scenario));
    }

    @Transactional(readOnly = true)
    public BillPayment get(UUID id) {
        return payments.findById(id)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "Ödeme bulunamadı"));
    }

    @Transactional(readOnly = true)
    public List<BillPayment> list(UUID accountId) {
        return payments.findTop50ByAccount_IdOrderByCreatedAtDesc(accountId);
    }

    @Transactional
    public void applyOutcome(UUID id, PaymentGateway.Outcome outcome) {
        BillPayment payment = payments.findByIdForUpdate(id)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "Ödeme bulunamadı"));
        if (payment.getStatus().equals("SUCCEEDED") || payment.getStatus().equals("FAILED")) return;

        String nextStatus = switch (outcome) {
            case ACCEPTED -> {
                transfers.transferReserved(payment.getAccountId(), CLEARING_ACCOUNT, payment.getAmount(), "bill-" + id);
                yield "SUCCEEDED";
            }
            case REJECTED -> {
                Account account = accounts.findByIdForUpdate(payment.getAccountId()).orElseThrow();
                account.release(payment.getAmount());
                yield "FAILED";
            }
            case UNKNOWN -> "UNKNOWN";
        };
        payment.status(nextStatus);
    }

    private BillPayment matching(BillPayment existing, UUID accountId, BigDecimal amount, String scenario) {
        if (!existing.getAccountId().equals(accountId)
                || existing.getAmount().compareTo(amount) != 0 || !existing.getScenario().equals(scenario)) {
            throw new DomainException("IDEMPOTENCY_CONFLICT", "Bu anahtar farklı bir ödemede kullanılmış");
        }
        return existing;
    }
}
