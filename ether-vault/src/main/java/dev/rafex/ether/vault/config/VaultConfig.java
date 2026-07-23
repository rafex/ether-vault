package dev.rafex.ether.vault.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.Arrays;
import java.util.Map;

public record VaultConfig(String bootstrapUsername, String bootstrapPassword, Set<String> bootstrapScopes,
        Duration tokenTtl, Path databasePath, Path vaultRoot, String gopassBinary, String gpgHome) {

    public static VaultConfig fromEnv() {
        return fromEnv(System.getenv());
    }

    public static VaultConfig fromEnv(final Map<String, String> env) {
        final var username = required(env.get("AUTH_BOOTSTRAP_USERNAME"), "AUTH_BOOTSTRAP_USERNAME");
        final var password = required(env.get("AUTH_BOOTSTRAP_PASSWORD"), "AUTH_BOOTSTRAP_PASSWORD");
        final var scopes = Set.copyOf(Arrays.stream(value(env.get("AUTH_BOOTSTRAP_SCOPES"), "auth:admin vault:read vault:write")
                .split("\\s+")).filter(scope -> !scope.isBlank()).toList());
        final var tokenTtl = Duration.ofSeconds(Long.parseLong(value(env.get("AUTH_TOKEN_TTL_SECONDS"), "3600")));
        return new VaultConfig(username, password, scopes, tokenTtl,
                Path.of(value(env.get("DATABASE_PATH"), "./data/ether-vault.sqlite")),
                Path.of(value(env.get("VAULT_ROOT"), "./data/vaults")),
                value(env.get("GOPASS_BIN"), "gopass"),
                blankToNull(env.get("GPG_HOME")));
    }

    private static String required(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " must be configured");
        }
        return value;
    }

    private static String value(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
