package com.bener.corebank.security;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

@Component
@Profile("demo")
@Order(2)
public class DemoUserInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public DemoUserInitializer(JdbcTemplate jdbc, PasswordEncoder passwords) { this.jdbc = jdbc; this.passwords = passwords; }
    @Override public void run(ApplicationArguments args) {
        jdbc.update("INSERT INTO app_users (id, username, password_hash, customer_id, role, enabled, created_at) "
                        + "VALUES (?, ?, ?, ?, 'USER', true, CURRENT_TIMESTAMP) ON CONFLICT (username) DO NOTHING",
                UUID.fromString("30000000-0000-0000-0000-000000000001"), "bener", passwords.encode("Bener123!"),
                UUID.fromString("10000000-0000-0000-0000-000000000001"));
    }
}
