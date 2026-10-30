package com.bener.corebank.transfer;

import com.bener.corebank.account.Account;
import com.bener.corebank.account.AccountRepository;
import com.bener.corebank.shared.DomainException;
import com.bener.corebank.outbox.OutboxEvent;
import com.bener.corebank.outbox.OutboxRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {
    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerEntryRepository ledger;
    private final OutboxRepository outbox;

    public TransferService(AccountRepository accounts, TransferRepository transfers, LedgerEntryRepository ledger,
                           OutboxRepository outbox) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.ledger = ledger;
        this.outbox = outbox;
    }

    @Transactional
    public Transfer transfer(UUID sourceId, UUID destinationId, BigDecimal amount, String key) {
        return perform(sourceId, destinationId, amount, key, false);
    }

    @Transactional
    public Transfer transferReserved(UUID sourceId, UUID destinationId, BigDecimal amount, String key) {
        return perform(sourceId, destinationId, amount, key, true);
    }

    private Transfer perform(UUID sourceId, UUID destinationId, BigDecimal amount, String key, boolean reserved) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new DomainException("INVALID_KEY", "Geçerli bir Idempotency-Key gerekli");
        }
        if (sourceId.equals(destinationId)) {
            throw new DomainException("SAME_ACCOUNT", "Kaynak ve hedef hesap aynı olamaz");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.precision() - amount.scale() > 17) {
            throw new DomainException("INVALID_AMOUNT", "Tutar pozitif ve en fazla iki ondalıklı olmalı");
        }
        BigDecimal normalizedAmount = amount.setScale(2);
        Transfer existing = transfers.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            return matching(existing, sourceId, destinationId, normalizedAmount);
        }

        // A stable lock order prevents A->B and B->A transfers from deadlocking.
        UUID firstId = sourceId.compareTo(destinationId) < 0 ? sourceId : destinationId;
        UUID secondId = firstId.equals(sourceId) ? destinationId : sourceId;
        Account first = locked(firstId);
        Account second = locked(secondId);
        Account source = sourceId.equals(firstId) ? first : second;
        Account destination = destinationId.equals(secondId) ? second : first;

        // A duplicate request for the same accounts can have waited for the row lock.
        existing = transfers.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            return matching(existing, sourceId, destinationId, normalizedAmount);
        }
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new DomainException("CURRENCY_MISMATCH", "Hesap para birimleri farklı");
        }

        if (reserved) {
            source.release(normalizedAmount);
        }
        source.debit(normalizedAmount);
        destination.credit(normalizedAmount);
        Transfer transfer = transfers.save(new Transfer(key, source, destination, normalizedAmount));
        ledger.save(new LedgerEntry(transfer, source, "DEBIT", normalizedAmount));
        ledger.save(new LedgerEntry(transfer, destination, "CREDIT", normalizedAmount));
        UUID eventId = UUID.randomUUID();
        String payload = new StringBuilder("{\"eventId\":\"").append(eventId)
                .append("\",\"type\":\"TRANSFER_COMPLETED\",\"transferId\":\"")
                .append(transfer.getId()).append("\",\"sourceAccountId\":\"")
                .append(sourceId).append("\",\"destinationAccountId\":\"")
                .append(destinationId).append("\",\"amount\":").append(normalizedAmount)
                .append('}').toString();
        outbox.save(new OutboxEvent(eventId, transfer.getId(), "TRANSFER_COMPLETED", payload));
        return transfer;
    }

    @Transactional(readOnly = true)
    public Transfer getTransfer(UUID id) {
        return transfers.findById(id)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "Transfer bulunamadı"));
    }

    private Account locked(UUID id) {
        return accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "Hesap bulunamadı"));
    }

    private Transfer matching(Transfer existing, UUID sourceId, UUID destinationId, BigDecimal amount) {
        if (!existing.getSourceAccountId().equals(sourceId)
                || !existing.getDestinationAccountId().equals(destinationId)
                || existing.getAmount().compareTo(amount) != 0) {
            throw new DomainException("IDEMPOTENCY_CONFLICT", "Bu anahtar farklı bir transferde kullanılmış");
        }
        return existing;
    }
}
