package dev.rafex.ether.vault.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuthUser(UUID id, String username, String passwordHash, Set<String> scopes, boolean enabled,
        Instant createdAt) {

    public AuthUser {
        Objects.requireNonNull(id, "id");
        username = required(username, "username");
        passwordHash = required(passwordHash, "passwordHash");
        scopes = Set.copyOf(Objects.requireNonNull(scopes, "scopes"));
        Objects.requireNonNull(createdAt, "createdAt");
    }

    private static String required(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
