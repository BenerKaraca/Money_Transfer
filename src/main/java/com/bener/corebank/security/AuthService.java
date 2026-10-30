package com.bener.corebank.security;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final Clock clock;
    private final long tokenMinutes;
    public AuthService(AppUserRepository users, PasswordEncoder passwords, JwtEncoder encoder, Clock clock,
                       @Value("${corebank.security.token-minutes}") long tokenMinutes) {
        this.users = users; this.passwords = passwords; this.encoder = encoder; this.clock = clock; this.tokenMinutes = tokenMinutes;
    }
    public LoginResult login(String username, String password) {
        AppUser user = users.findByUsername(username).filter(AppUser::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Geçersiz kullanıcı adı veya parola"));
        if (!passwords.matches(password, user.getPasswordHash())) throw new BadCredentialsException("Geçersiz kullanıcı adı veya parola");
        Instant now = clock.instant();
        Instant expiresAt = now.plus(tokenMinutes, ChronoUnit.MINUTES);
        var claims = JwtClaimsSet.builder().issuer("corebank").subject(user.getUsername()).issuedAt(now).expiresAt(expiresAt)
                .claim("customer_id", user.getCustomerId().toString()).claim("roles", List.of(user.getRole())).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResult(token, expiresAt, user.getUsername(), user.getCustomerId(), user.getRole());
    }
    public record LoginResult(String accessToken, Instant expiresAt, String username, java.util.UUID customerId, String role) {}
}
