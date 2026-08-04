package dev.rafex.ether.vault.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SecretNamespace(UUID id, UUID vaultId, String path, Instant createdAt) {

    public SecretNamespace {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(vaultId, "vaultId");
        path = validatePath(path);
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static SecretNamespace create(final UUID vaultId, final String path) {
        return new SecretNamespace(UUID.randomUUID(), vaultId, path, Instant.now());
    }

    private static String validatePath(final String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Secret.validatePath(value);
    }
}
