package com.bener.corebank;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
@Order(1)
public class DemoDataInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public DemoDataInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedCustomer("10000000-0000-0000-0000-000000000001", "Demo Sender");
        seedCustomer("10000000-0000-0000-0000-000000000002", "Demo Receiver");
        seedCustomer("10000000-0000-0000-0000-000000000003", "Ayşe Demir");
        seedAccount("20000000-0000-0000-0000-000000000001", "10000000-0000-0000-0000-000000000001", "5000.00");
        seedAccount("20000000-0000-0000-0000-000000000002", "10000000-0000-0000-0000-000000000002", "0.00");
        seedAccount("20000000-0000-0000-0000-000000000003", "10000000-0000-0000-0000-000000000003", "0.00");
        seedAccount("20000000-0000-0000-0000-000000000004", "10000000-0000-0000-0000-000000000001", "EUR", "0.00");
        seedAccount("20000000-0000-0000-0000-000000000005", "10000000-0000-0000-0000-000000000001", "USD", "0.00");
    }

    private void seedCustomer(String id, String name) {
        jdbc.update("INSERT INTO customers (id, full_name, created_at) VALUES (?, ?, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING",
                UUID.fromString(id), name);
    }

    private void seedAccount(String id, String customerId, String balance) {
        seedAccount(id, customerId, "TRY", balance);
    }

    private void seedAccount(String id, String customerId, String currency, String balance) {
        jdbc.update("INSERT INTO accounts (id, customer_id, currency, balance, version, created_at) "
                        + "VALUES (?, ?, ?, ?, 0, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING",
                UUID.fromString(id), UUID.fromString(customerId), currency, new BigDecimal(balance));
    }
}
