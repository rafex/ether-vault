package dev.rafex.ether.vault.application.service;

import java.util.Objects;
import java.util.UUID;

import dev.rafex.ether.vault.application.port.in.InsertSecretUseCase;
import dev.rafex.ether.vault.application.port.in.ReadSecretUseCase;
import dev.rafex.ether.vault.application.port.out.SecretCatalog;
import dev.rafex.ether.vault.application.port.out.SecretStore;
import dev.rafex.ether.vault.application.port.out.VaultRepository;
import dev.rafex.ether.vault.domain.model.Secret;
import dev.rafex.ether.vault.domain.model.SecretReference;
import dev.rafex.ether.vault.domain.model.Vault;

public final class SecretService implements InsertSecretUseCase, ReadSecretUseCase {

    private final VaultRepository vaults;
    private final SecretStore store;
    private final SecretCatalog catalog;

    public SecretService(final VaultRepository vaults, final SecretStore store, final SecretCatalog catalog) {
        this.vaults = Objects.requireNonNull(vaults, "vaults");
        this.store = Objects.requireNonNull(store, "store");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public SecretReference insert(final InsertSecretUseCase.InsertSecretCommand command) {
        Objects.requireNonNull(command, "command");
        final var vault = vault(command.vaultId());
        final var details = details(command);
        if (catalog.findByPath(vault.id(), details.path()).isPresent()) {
            throw new SecretAlreadyExistsException(details.path());
        }
        final var namespace = catalog.ensureNamespace(vault.id(), details.namespacePath());
        final var secret = new Secret(vault.id(), details.path(), command.value());
        store.put(vault.id(), vault.homePath(), secret.path(), secret.value());
        return catalog.save(SecretReference.create(vault.id(), namespace.id(), details.shortName(),
                command.friendlyName(), secret.path()));
    }

    public void insert(final UUID vaultId, final String path, final String value) {
        insert(new InsertSecretUseCase.InsertSecretCommand(vaultId, null, null, path, path, value));
    }

    @Override
    public ReadSecretUseCase.SecretRead read(final UUID vaultId, final ReadSecretUseCase.SecretSelector selector) {
        final var vault = vault(vaultId);
        final var reference = resolve(vault.id(), selector);
        return new ReadSecretUseCase.SecretRead(reference, store.get(vault.id(), vault.homePath(), reference.path()));
    }

    public String read(final UUID vaultId, final String path) {
        return read(vaultId, new ReadSecretUseCase.SecretSelector(null, null, null, path)).value();
    }

    private SecretReference resolve(final UUID vaultId, final ReadSecretUseCase.SecretSelector selector) {
        if (selector == null || selector.populatedFields() != 1) {
            throw new IllegalArgumentException("exactly one of id, shortName, friendlyName or path is required");
        }
        if (selector.id() != null) {
            return catalog.findById(vaultId, selector.id()).orElseThrow(SecretNotFoundException::new);
        }
        if (selector.path() != null) {
            return catalog.findByPath(vaultId, Secret.validatePath(selector.path()))
                    .orElseThrow(SecretNotFoundException::new);
        }
        final var matches = selector.shortName() != null
                ? catalog.findByShortName(vaultId, Secret.validateSegment(selector.shortName(), "shortName"))
                : catalog.findByFriendlyName(vaultId, requiredFriendlyName(selector.friendlyName()));
        if (matches.isEmpty()) {
            throw new SecretNotFoundException();
        }
        if (matches.size() > 1) {
            throw new AmbiguousSecretException();
        }
        return matches.get(0);
    }

    private SecretDetails details(final InsertSecretUseCase.InsertSecretCommand command) {
        final var requestedPath = command.path() == null || command.path().isBlank() ? null
                : Secret.validatePath(command.path());
        if (requestedPath != null) {
            final var separator = requestedPath.lastIndexOf('/');
            final var namespacePath = separator < 0 ? "" : requestedPath.substring(0, separator);
            final var shortName = requestedPath.substring(separator + 1);
            if (command.namespace() != null && !command.namespace().isBlank()
                    && !namespacePath.equals(Secret.validatePath(command.namespace()))) {
                throw new IllegalArgumentException("namespace does not match path");
            }
            if (command.shortName() != null && !command.shortName().isBlank()
                    && !shortName.equals(Secret.validateSegment(command.shortName(), "shortName"))) {
                throw new IllegalArgumentException("shortName does not match path");
            }
            return new SecretDetails(namespacePath, shortName, requestedPath);
        }
        final var shortName = Secret.validateSegment(command.shortName(), "shortName");
        final var namespacePath = command.namespace() == null || command.namespace().isBlank() ? ""
                : Secret.validatePath(command.namespace());
        final var path = namespacePath.isBlank() ? shortName : namespacePath + "/" + shortName;
        return new SecretDetails(namespacePath, shortName, Secret.validatePath(path));
    }

    private static String requiredFriendlyName(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("friendlyName is required");
        }
        return value.trim();
    }

    private Vault vault(final UUID id) {
        return vaults.findById(Objects.requireNonNull(id, "vaultId"))
                .orElseThrow(() -> new VaultNotFoundException(id));
    }

    public static final class VaultNotFoundException extends RuntimeException {
        public VaultNotFoundException(final UUID id) {
            super("vault not found: " + id);
        }
    }

    public static final class SecretNotFoundException extends RuntimeException {
        public SecretNotFoundException() {
            super("secret not found");
        }
    }

    public static final class AmbiguousSecretException extends RuntimeException {
        public AmbiguousSecretException() {
            super("secret selector matches more than one secret");
        }
    }

    public static final class SecretAlreadyExistsException extends RuntimeException {
        public SecretAlreadyExistsException(final String path) {
            super("secret already exists: " + path);
        }
    }

    private record SecretDetails(String namespacePath, String shortName, String path) {
    }
}
