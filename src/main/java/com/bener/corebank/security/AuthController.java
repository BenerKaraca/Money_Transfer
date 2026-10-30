package com.bener.corebank.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    @PostMapping("/login")
    public AuthService.LoginResult login(@Valid @RequestBody LoginRequest request) { return auth.login(request.username(), request.password()); }
    public record LoginRequest(@NotBlank @Size(max = 80) String username, @NotBlank @Size(max = 120) String password) {}
}
