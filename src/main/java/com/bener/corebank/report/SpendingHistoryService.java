package com.bener.corebank.report;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpendingHistoryService {
    private final JdbcTemplate jdbc;
    public SpendingHistoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public History history(UUID customerId) {
        String sql = "SELECT id, type, description, currency, amount, occurred_at FROM ("
                + "SELECT t.id, CASE WHEN t.destination_account_id = 'ffffffff-ffff-ffff-ffff-fffffffffff1' "
                + "THEN 'BILL_PAYMENT' ELSE 'TRANSFER' END type, "
                + "CASE WHEN t.destination_account_id = 'ffffffff-ffff-ffff-ffff-fffffffffff1' THEN 'Fatura ödemesi' "
                + "ELSE 'Transfer · ' || c.full_name END description, a.currency, le.amount, le.created_at occurred_at "
                + "FROM ledger_entries le JOIN accounts a ON a.id=le.account_id JOIN transfers t ON t.id=le.transfer_id "
                + "JOIN accounts da ON da.id=t.destination_account_id JOIN customers c ON c.id=da.customer_id "
                + "WHERE a.customer_id=? AND le.direction='DEBIT' "
                + "UNION ALL SELECT f.id, 'FX_EXCHANGE', 'Döviz alımı · ' || da.currency, sa.currency, "
                + "f.source_amount, f.created_at FROM fx_trades f JOIN accounts sa ON sa.id=f.source_account_id "
                + "JOIN accounts da ON da.id=f.destination_account_id WHERE sa.customer_id=?"
                + ") spending ORDER BY occurred_at DESC LIMIT 100";
        List<Item> items = jdbc.query(sql, this::item, customerId, customerId);
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        items.forEach(item -> totals.merge(item.currency(), item.amount(), BigDecimal::add));
        return new History(items, totals);
    }

    private Item item(ResultSet rs, int row) throws SQLException {
        return new Item(rs.getObject("id", UUID.class), rs.getString("type"), rs.getString("description"),
                rs.getString("currency"), rs.getBigDecimal("amount"), rs.getTimestamp("occurred_at").toInstant());
    }
    public record Item(UUID id, String type, String description, String currency, BigDecimal amount, Instant occurredAt) {}
    public record History(List<Item> items, Map<String, BigDecimal> totalsByCurrency) {}
}
