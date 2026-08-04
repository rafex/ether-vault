package dev.rafex.ether.vault.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import dev.rafex.ether.vault.domain.model.AuthPrincipal;
import dev.rafex.ether.vault.domain.model.AuthUser;

public interface AuthRepository {
    Optional<AuthUser> findUserByUsername(String username);

    AuthUser saveUser(AuthUser user);

    Optional<AuthPrincipal> findPrincipalByTokenHash(String tokenHash, Instant now);

    void saveToken(UUID userId, String tokenHash, Instant expiresAt);
}
