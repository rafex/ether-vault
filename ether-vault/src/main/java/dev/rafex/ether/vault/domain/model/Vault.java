package dev.rafex.ether.vault.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record Vault(UUID id, String name, String recipient, String description, String homePath,
        VaultStatus status, Instant createdAt) {

    private static final Pattern NAME = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    public Vault {
        Objects.requireNonNull(id, "id");
        name = required(name, "name");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("name must be a lowercase slug");
        }
        recipient = required(recipient, "recipient");
        if (recipient.startsWith("-") || recipient.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("recipient contains invalid characters");
        }
        description = description == null ? "" : description.trim();
        homePath = required(homePath, "homePath");
        status = Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Vault create(final String name, final String recipient, final String description,
            final String homePath) {
        return new Vault(UUID.randomUUID(), name.trim(), recipient.trim(), description, homePath,
                VaultStatus.PROVISIONING, Instant.now());
    }

    public Vault ready() {
        return new Vault(id, name, recipient, description, homePath, VaultStatus.READY, createdAt);
    }

    private static String required(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
