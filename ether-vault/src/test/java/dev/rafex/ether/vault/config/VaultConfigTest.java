package dev.rafex.ether.vault.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class VaultConfigTest {

    @Test
    void requiresApiKey() {
        assertThrows(IllegalStateException.class, () -> VaultConfig.fromEnv(Map.of()));
    }

    @Test
    void appliesSafeDefaults() {
        final var config = VaultConfig.fromEnv(Map.of("AUTH_BOOTSTRAP_USERNAME", "admin",
                "AUTH_BOOTSTRAP_PASSWORD", "test-password-123"));

        assertEquals("admin", config.bootstrapUsername());
        assertEquals(3600, config.tokenTtl().toSeconds());
        assertEquals("gopass", config.gopassBinary());
        assertEquals("./data/ether-vault.sqlite", config.databasePath().toString());
    }
}
