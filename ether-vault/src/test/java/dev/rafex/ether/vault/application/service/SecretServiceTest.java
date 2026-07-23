package dev.rafex.ether.vault.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.rafex.ether.vault.application.port.in.InsertSecretUseCase;
import dev.rafex.ether.vault.application.port.in.ReadSecretUseCase;
import dev.rafex.ether.vault.application.port.out.SecretCatalog;
import dev.rafex.ether.vault.application.port.out.SecretStore;
import dev.rafex.ether.vault.application.port.out.VaultRepository;
import dev.rafex.ether.vault.domain.model.SecretNamespace;
import dev.rafex.ether.vault.domain.model.SecretReference;
import dev.rafex.ether.vault.domain.model.Vault;

class SecretServiceTest {

    @Test
    void insertsAndReadsSecretByPathAndId() {
        final var repository = new InMemoryRepository();
        final var store = new RecordingSecretStore();
        final var catalog = new InMemoryCatalog();
        final var vault = Vault.create("personal", "alice@example.com", null, "/vaults").ready();
        repository.save(vault);
        final var service = new SecretService(repository, store, catalog);

        final var reference = service.insert(new InsertSecretUseCase.InsertSecretCommand(vault.id(), "personal",
                "email", "Personal email", null, "hunter2"));

        assertEquals(vault.id(), store.vaultId);
        assertEquals("/vaults", store.vaultHomeRoot);
        assertEquals("personal/email", store.path);
        assertEquals("hunter2", store.value);
        assertEquals("hunter2", service.read(vault.id(), "personal/email"));
        assertEquals("hunter2", service.read(vault.id(), new ReadSecretUseCase.SecretSelector(reference.id(), null,
                null, null)).value());
    }

    @Test
    void rejectsUnknownVault() {
        final var service = new SecretService(new InMemoryRepository(), new RecordingSecretStore(),
                new InMemoryCatalog());

        assertThrows(SecretService.VaultNotFoundException.class,
                () -> service.read(UUID.randomUUID(), "personal/email"));
    }

    @Test
    void rejectsUnsafePathsBeforeCallingStore() {
        final var repository = new InMemoryRepository();
        final var store = new RecordingSecretStore();
        final var vault = Vault.create("personal", "alice@example.com", null, "/vaults").ready();
        repository.save(vault);
        final var service = new SecretService(repository, store, new InMemoryCatalog());

        assertThrows(IllegalArgumentException.class, () -> service.insert(vault.id(), "../escape", "value"));
        assertEquals(null, store.path);
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

    private static final class InMemoryCatalog implements SecretCatalog {
        private final Map<UUID, SecretNamespace> namespaces = new HashMap<>();
        private final List<SecretReference> references = new ArrayList<>();

        @Override
        public SecretNamespace ensureNamespace(final UUID vaultId, final String path) {
            return namespaces.computeIfAbsent(UUID.nameUUIDFromBytes((vaultId + ":" + path).getBytes()),
                    ignored -> SecretNamespace.create(vaultId, path));
        }

        @Override
        public List<SecretNamespace> listNamespaces(final UUID vaultId) {
            return namespaces.values().stream().filter(value -> value.vaultId().equals(vaultId)).toList();
        }

        @Override
        public SecretReference save(final SecretReference reference) {
            references.add(reference);
            return reference;
        }

        @Override
        public Optional<SecretReference> findById(final UUID vaultId, final UUID id) {
            return references.stream().filter(value -> value.vaultId().equals(vaultId) && value.id().equals(id))
                    .findFirst();
        }

        @Override
        public Optional<SecretReference> findByPath(final UUID vaultId, final String path) {
            return references.stream().filter(value -> value.vaultId().equals(vaultId) && value.path().equals(path))
                    .findFirst();
        }

        @Override
        public List<SecretReference> findByShortName(final UUID vaultId, final String shortName) {
            return references.stream().filter(value -> value.vaultId().equals(vaultId)
                    && value.shortName().equals(shortName)).toList();
        }

        @Override
        public List<SecretReference> findByFriendlyName(final UUID vaultId, final String friendlyName) {
            return references.stream().filter(value -> value.vaultId().equals(vaultId)
                    && value.friendlyName().equals(friendlyName)).toList();
        }
    }

    private static final class RecordingSecretStore implements SecretStore {
        private UUID vaultId;
        private String vaultHomeRoot;
        private String path;
        private String value;

        @Override
        public void put(final UUID vaultId, final String vaultHomeRoot, final String path, final String value) {
            this.vaultId = vaultId;
            this.vaultHomeRoot = vaultHomeRoot;
            this.path = path;
            this.value = value;
        }

        @Override
        public String get(final UUID vaultId, final String vaultHomeRoot, final String path) {
            return value;
        }
    }
}
