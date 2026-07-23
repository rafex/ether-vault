package dev.rafex.ether.vault.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.rafex.ether.vault.application.port.in.CreateVaultUseCase.CreateVaultCommand;
import dev.rafex.ether.vault.application.port.out.VaultProvisioner;
import dev.rafex.ether.vault.application.port.out.VaultRepository;
import dev.rafex.ether.vault.domain.model.Vault;

class VaultServiceTest {

    @Test
    void provisionsAndPersistsReadyVault() {
        final var repository = new InMemoryRepository();
        final var provisioner = new RecordingProvisioner();
        final var service = new VaultService(repository, provisioner, "/vaults");

        final var created = service.create(new CreateVaultCommand("personal", "alice@example.com", "Personal"));

        assertEquals("personal", created.name());
        assertEquals("READY", created.status().name());
        assertEquals(created, repository.findById(created.id()).orElseThrow());
        assertEquals(created.id(), provisioner.provisioned);
    }

    @Test
    void rejectsDuplicateNamesBeforeProvisioning() {
        final var repository = new InMemoryRepository();
        final var provisioner = new RecordingProvisioner();
        final var service = new VaultService(repository, provisioner, "/vaults");
        service.create(new CreateVaultCommand("personal", "alice@example.com", null));

        assertThrows(VaultService.VaultAlreadyExistsException.class,
                () -> service.create(new CreateVaultCommand("personal", "bob@example.com", null)));
        assertEquals(1, provisioner.calls);
    }

    @Test
    void validatesNamesInTheDomain() {
        final var service = new VaultService(new InMemoryRepository(), new RecordingProvisioner(), "/vaults");

        assertThrows(IllegalArgumentException.class,
                () -> service.create(new CreateVaultCommand("../escape", "alice@example.com", null)));
    }

    private static final class InMemoryRepository implements VaultRepository {
        private final Map<UUID, Vault> values = new HashMap<>();

        @Override
        public Vault save(final Vault vault) {
            values.put(vault.id(), vault);
            return vault;
        }

        @Override
        public Optional<Vault> findById(final UUID id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public boolean existsByName(final String name) {
            return values.values().stream().anyMatch(value -> value.name().equals(name));
        }
    }

    private static final class RecordingProvisioner implements VaultProvisioner {
        private UUID provisioned;
        private int calls;

        @Override
        public void provision(final Vault vault) {
            provisioned = vault.id();
            calls++;
        }

        @Override
        public void cleanup(final Vault vault) {
            provisioned = null;
        }
    }
}
