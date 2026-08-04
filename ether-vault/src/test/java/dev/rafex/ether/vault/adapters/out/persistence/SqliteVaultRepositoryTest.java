package dev.rafex.ether.vault.adapters.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.rafex.ether.vault.domain.model.Vault;

class SqliteVaultRepositoryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void createsSchemaAndRoundTripsVaultMetadata() {
        final var repository = new SqliteVaultRepository(tempDirectory.resolve("vaults.sqlite"));
        final var vault = Vault.create("personal", "alice@example.com", "Personal", "/vaults").ready();

        repository.save(vault);

        final var loaded = repository.findById(vault.id()).orElseThrow();
        assertEquals(vault, loaded);
        assertTrue(repository.existsByName("personal"));
    }
}
