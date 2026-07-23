package dev.rafex.ether.vault.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SecretReference(UUID id, UUID vaultId, UUID namespaceId, String shortName, String friendlyName,
        String path, Instant createdAt, Instant updatedAt) {

    public SecretReference {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(vaultId, "vaultId");
        Objects.requireNonNull(namespaceId, "namespaceId");
        shortName = Secret.validateSegment(shortName, "shortName");
        friendlyName = requiredFriendlyName(friendlyName);
        path = Secret.validatePath(path);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static SecretReference create(final UUID vaultId, final UUID namespaceId, final String shortName,
            final String friendlyName, final String path) {
        final var now = Instant.now();
        return new SecretReference(UUID.randomUUID(), vaultId, namespaceId, shortName, friendlyName, path, now, now);
    }

    private static String requiredFriendlyName(final String value) {
        if (value == null || value.isBlank() || value.trim().length() > 160
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("friendlyName must be between 1 and 160 printable characters");
        }
        return value.trim();
    }
}
