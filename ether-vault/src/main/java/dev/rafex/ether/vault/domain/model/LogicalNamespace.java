package dev.rafex.ether.vault.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LogicalNamespace(UUID id, String name, String description, Instant createdAt) {

    public LogicalNamespace {
        Objects.requireNonNull(id, "id");
        name = requiredSlug(name);
        description = description == null ? "" : description.trim();
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static LogicalNamespace create(final String name, final String description) {
        return new LogicalNamespace(UUID.randomUUID(), name, description, Instant.now());
    }

    private static String requiredSlug(final String value) {
        if (value == null || value.isBlank() || !value.trim().matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) {
            throw new IllegalArgumentException("namespace name must be a lowercase slug");
        }
        return value.trim();
    }
}
