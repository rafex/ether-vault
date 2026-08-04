package dev.rafex.ether.vault.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuthPrincipal(UUID userId, String username, Set<String> scopes, Instant expiresAt) {

    public AuthPrincipal {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(username, "username");
        scopes = Set.copyOf(Objects.requireNonNull(scopes, "scopes"));
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean hasScope(final String scope) {
        return scopes.contains(scope);
    }
}
