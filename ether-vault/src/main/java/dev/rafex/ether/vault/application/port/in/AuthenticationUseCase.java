package dev.rafex.ether.vault.application.port.in;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import dev.rafex.ether.vault.domain.model.AuthPrincipal;

public interface AuthenticationUseCase {
    AuthToken login(String username, String password);

    Optional<AuthPrincipal> authenticate(String token);

    record AuthToken(String accessToken, Instant expiresAt, Set<String> scopes) {
    }
}
