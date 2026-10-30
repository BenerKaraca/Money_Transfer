package com.bener.corebank.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bener.corebank.account.Account;
import com.bener.corebank.account.AccountRepository;
import com.bener.corebank.customer.Customer;
import com.bener.corebank.report.SpendingHistoryController;
import com.bener.corebank.report.SpendingHistoryService;
import com.bener.corebank.shared.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityCoverageTest {
    private final UUID customerId = UUID.randomUUID();

    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test void authenticatesEnabledUserAndBuildsSignedJwtClaims() {
        AppUserRepository users = mock(AppUserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        JwtEncoder encoder = mock(JwtEncoder.class);
        AppUser user = user(true);
        when(users.findByUsername("bener")).thenReturn(Optional.of(user));
        when(passwords.matches("secret", "hash")).thenReturn(true);
        Jwt encoded = Jwt.withTokenValue("signed-token").header("alg", "HS256").claim("sub", "bener").build();
        when(encoder.encode(any(JwtEncoderParameters.class))).thenReturn(encoded);
        Instant now = Instant.parse("2026-10-30T10:00:00Z");
        AuthService service = new AuthService(users, passwords, encoder, Clock.fixed(now, ZoneOffset.UTC), 30);

        var result = service.login("bener", "secret");
        assertThat(result.accessToken()).isEqualTo("signed-token");
        assertThat(result.expiresAt()).isEqualTo(now.plusSeconds(1800));
        assertThat(result.username()).isEqualTo("bener");
        assertThat(result.customerId()).isEqualTo(customerId);
        assertThat(result.role()).isEqualTo("USER");
        assertThat(new AuthController(service).login(new AuthController.LoginRequest("bener", "secret"))).isEqualTo(result);
    }

    @Test void rejectsMissingDisabledAndWrongPasswordUsers() {
        AppUserRepository users = mock(AppUserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        AuthService service = new AuthService(users, passwords, mock(JwtEncoder.class), Clock.systemUTC(), 30);
        when(users.findByUsername("missing")).thenReturn(Optional.empty());
        when(users.findByUsername("disabled")).thenReturn(Optional.of(user(false)));
        AppUser enabled = user(true);
        when(users.findByUsername("bener")).thenReturn(Optional.of(enabled));
        when(passwords.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("missing", "x")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.login("disabled", "x")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.login("bener", "wrong")).isInstanceOf(BadCredentialsException.class);
    }

    @Test void configuresBcryptHmacAndRoleMapping() {
        SecurityConfiguration configuration = new SecurityConfiguration();
        PasswordEncoder passwordEncoder = configuration.passwordEncoder();
        assertThat(passwordEncoder.matches("secret", passwordEncoder.encode("secret"))).isTrue();
        assertThatThrownBy(() -> configuration.jwtKey("short")).isInstanceOf(IllegalStateException.class);
        SecretKey key = configuration.jwtKey("this-is-a-demo-secret-with-more-than-32-bytes");
        assertThat(configuration.jwtEncoder(key)).isNotNull();
        assertThat(configuration.jwtDecoder(key)).isNotNull();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").claim("sub", "bener")
                .claim("roles", List.of("USER", "ADMIN")).build();
        var authentication = configuration.jwtAuthenticationConverter().convert(jwt);
        assertThat(authentication.getAuthorities()).extracting("authority").contains("ROLE_USER", "ROLE_ADMIN");
    }

    @Test void enforcesAuthenticatedCustomerAndAccountOwnership() {
        AccountRepository accounts = mock(AccountRepository.class);
        BankAuthorization authorization = new BankAuthorization(accounts);
        assertThatThrownBy(authorization::customerId).isInstanceOf(DomainException.class);

        authenticate(customerId);
        assertThat(authorization.customerId()).isEqualTo(customerId);
        Customer owner = new Customer("Owner");
        ReflectionTestUtils.setField(owner, "id", customerId);
        Account own = new Account(owner);
        UUID accountId = own.getId();
        when(accounts.findById(accountId)).thenReturn(Optional.of(own));
        authorization.requireOwnAccount(accountId);
        authorization.requireOwnCustomer(customerId);

        UUID absent = UUID.randomUUID();
        when(accounts.findById(absent)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authorization.requireOwnAccount(absent)).isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("NOT_FOUND");
        Customer other = new Customer("Other");
        Account foreign = new Account(other);
        when(accounts.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> authorization.requireOwnAccount(foreign.getId())).isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("FORBIDDEN");
        assertThatThrownBy(() -> authorization.requireOwnCustomer(UUID.randomUUID())).isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("FORBIDDEN");
    }

    @Test void seedsDemoUserAndMapsSpendingController() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(passwords.encode("Bener123!")).thenReturn("bcrypt-hash");
        new DemoUserInitializer(jdbc, passwords).run(null);
        verify(jdbc).update(any(String.class), any(UUID.class), any(String.class), any(String.class), any(UUID.class));

        SpendingHistoryService spending = mock(SpendingHistoryService.class);
        BankAuthorization authorization = mock(BankAuthorization.class);
        var expected = new SpendingHistoryService.History(List.of(), Map.of());
        when(authorization.customerId()).thenReturn(customerId);
        when(spending.history(customerId)).thenReturn(expected);
        assertThat(new SpendingHistoryController(spending, authorization).history()).isSameAs(expected);
    }

    @Test void exposesEveryMappedUserField() {
        AppUser user = user(true);
        assertThat(user.getId()).isNotNull();
        assertThat(user.getUsername()).isEqualTo("bener");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getCustomerId()).isEqualTo(customerId);
        assertThat(user.getRole()).isEqualTo("USER");
        assertThat(user.isEnabled()).isTrue();
    }

    private AppUser user(boolean enabled) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "username", "bener");
        ReflectionTestUtils.setField(user, "passwordHash", "hash");
        ReflectionTestUtils.setField(user, "customerId", customerId);
        ReflectionTestUtils.setField(user, "role", "USER");
        ReflectionTestUtils.setField(user, "enabled", enabled);
        ReflectionTestUtils.setField(user, "createdAt", Instant.now());
        return user;
    }

    private void authenticate(UUID id) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").claim("customer_id", id.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
