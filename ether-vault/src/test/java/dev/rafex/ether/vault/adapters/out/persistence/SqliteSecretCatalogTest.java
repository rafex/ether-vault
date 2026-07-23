package dev.rafex.ether.vault.adapters.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.rafex.ether.vault.domain.model.SecretReference;
import dev.rafex.ether.vault.domain.model.Vault;

class SqliteSecretCatalogTest {

    @TempDir
    Path tempDirectory;

    @Test
    void storesNamespaceAndResolvesReferenceByAllNames() {
        final var database = tempDirectory.resolve("vault.sqlite");
        final var vaults = new SqliteVaultRepository(database);
        final var catalog = new SqliteSecretCatalog(database);
        final var vault = Vault.create("personal", "alice@example.com", null, "/vaults").ready();
        vaults.save(vault);
        final var namespace = catalog.ensureNamespace(vault.id(), "personal");
        final var reference = catalog.save(SecretReference.create(vault.id(), namespace.id(), "email",
                "Personal email", "personal/email"));

        assertEquals(reference, catalog.findById(vault.id(), reference.id()).orElseThrow());
        assertEquals(reference, catalog.findByPath(vault.id(), "personal/email").orElseThrow());
        assertEquals(List.of(reference), catalog.findByShortName(vault.id(), "email"));
        assertEquals(List.of(reference), catalog.findByFriendlyName(vault.id(), "Personal email"));
        assertTrue(catalog.listNamespaces(vault.id()).contains(namespace));
    }
}
