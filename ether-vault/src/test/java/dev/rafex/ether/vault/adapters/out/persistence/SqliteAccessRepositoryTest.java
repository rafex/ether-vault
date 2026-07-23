package dev.rafex.ether.vault.adapters.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.rafex.ether.vault.domain.model.LogicalNamespace;
import dev.rafex.ether.vault.domain.model.Role;
import dev.rafex.ether.vault.domain.model.Vault;

class SqliteAccessRepositoryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void persistsRolesAndNamespaceVaultRelation() {
        final var database = tempDirectory.resolve("access.sqlite");
        final var vaults = new SqliteVaultRepository(database);
        final var access = new SqliteAccessRepository(database);
        final var namespace = LogicalNamespace.create("team", "Team namespace");
        final var vault = Vault.create("personal", "alice@example.com", null, "/vaults").ready();
        final var userId = UUID.randomUUID();
        access.saveNamespace(namespace);
        vaults.save(vault);
        access.attachVault(namespace.id(), vault.id());
        access.grantNamespaceRole(namespace.id(), userId, Role.WRITE);
        access.grantVaultRole(vault.id(), userId, Role.READ);

        assertTrue(access.vaultBelongsToNamespace(namespace.id(), vault.id()));
        assertEquals(Role.WRITE, access.findNamespaceRole(namespace.id(), userId).orElseThrow());
        assertEquals(Role.READ, access.findVaultRole(vault.id(), userId).orElseThrow());
    }
}
