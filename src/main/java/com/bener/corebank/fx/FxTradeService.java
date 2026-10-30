package com.bener.corebank.fx;

import com.bener.corebank.account.Account;
import com.bener.corebank.account.AccountRepository;
import com.bener.corebank.shared.DomainException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FxTradeService {
    private static final String COLUMNS = "id, quote_id, source_account_id, destination_account_id, "
            + "source_amount, target_amount, rate, rate_date, created_at";
    private final AccountRepository accounts;
    private final FxQuoteClient quotes;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public FxTradeService(AccountRepository accounts, FxQuoteClient quotes, JdbcTemplate jdbc, Clock clock) {
        this.accounts = accounts;
        this.quotes = quotes;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public Trade execute(UUID sourceId, UUID destinationId, UUID quoteId, String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new DomainException("INVALID_KEY", "Geçerli bir Idempotency-Key gerekli");
        }
        if (sourceId.equals(destinationId)) {
            throw new DomainException("SAME_ACCOUNT", "Kaynak ve hedef hesap aynı olamaz");
        }
        Trade existing = byKey(key);
        if (existing != null) return matching(existing, sourceId, destinationId, quoteId);

        FxQuoteClient.Quote quote = quotes.get(quoteId);
        if (quote == null || !Instant.now(clock).isBefore(quote.expiresAt())) {
            throw new DomainException("EXPIRED_QUOTE", "Döviz teklifinin süresi doldu");
        }

        UUID firstId = sourceId.compareTo(destinationId) < 0 ? sourceId : destinationId;
        UUID secondId = firstId.equals(sourceId) ? destinationId : sourceId;
        Account first = locked(firstId);
        Account second = locked(secondId);
        Account source = firstId.equals(sourceId) ? first : second;
        Account destination = secondId.equals(destinationId) ? second : first;
        existing = byKey(key);
        if (existing != null) return matching(existing, sourceId, destinationId, quoteId);

        if (!source.getCustomerId().equals(destination.getCustomerId())
                || !source.getCurrency().equals(quote.sourceCurrency())
                || !destination.getCurrency().equals(quote.targetCurrency())) {
            throw new DomainException("INVALID_FX_ACCOUNTS", "Döviz işlemi aynı müşterinin uygun para birimli hesapları arasında olmalı");
        }
        source.debit(quote.sourceAmount());
        destination.credit(quote.targetAmount());
        Trade result = new Trade(UUID.randomUUID(), quote.id(), sourceId, destinationId,
                quote.sourceAmount(), quote.targetAmount(), quote.rate(), quote.rateDate(), Instant.now(clock));
        jdbc.update("INSERT INTO fx_trades (id, idempotency_key, quote_id, source_account_id, "
                        + "destination_account_id, source_amount, target_amount, rate, rate_date, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", result.id(), key, result.quoteId(),
                sourceId, destinationId, result.sourceAmount(), result.targetAmount(), result.rate(),
                result.rateDate(), Timestamp.from(result.createdAt()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Trade> list(UUID accountId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM fx_trades WHERE source_account_id = ? "
                        + "OR destination_account_id = ? ORDER BY created_at DESC LIMIT 50",
                this::trade, accountId, accountId);
    }

    private Trade byKey(String key) {
        return jdbc.query("SELECT " + COLUMNS + " FROM fx_trades WHERE idempotency_key = ?",
                this::trade, key).stream().findFirst().orElse(null);
    }

    private Account locked(UUID id) {
        return accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "Hesap bulunamadı"));
    }

    private Trade matching(Trade trade, UUID sourceId, UUID destinationId, UUID quoteId) {
        if (!trade.sourceAccountId().equals(sourceId) || !trade.destinationAccountId().equals(destinationId)
                || !trade.quoteId().equals(quoteId)) {
            throw new DomainException("IDEMPOTENCY_CONFLICT", "Bu anahtar farklı bir döviz işleminde kullanılmış");
        }
        return trade;
    }

    private Trade trade(ResultSet rs, int row) throws SQLException {
        return new Trade(rs.getObject("id", UUID.class), rs.getObject("quote_id", UUID.class),
                rs.getObject("source_account_id", UUID.class), rs.getObject("destination_account_id", UUID.class),
                rs.getBigDecimal("source_amount"), rs.getBigDecimal("target_amount"), rs.getBigDecimal("rate"),
                rs.getDate("rate_date").toLocalDate(), rs.getTimestamp("created_at").toInstant());
    }

    public record Trade(UUID id, UUID quoteId, UUID sourceAccountId, UUID destinationAccountId,
                        BigDecimal sourceAmount, BigDecimal targetAmount, BigDecimal rate,
                        LocalDate rateDate, Instant createdAt) {}
}
