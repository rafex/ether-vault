package dev.rafex.ether.vault.application.service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import dev.rafex.ether.vault.application.port.in.CreateVaultUseCase;
import dev.rafex.ether.vault.application.port.in.GetVaultUseCase;
import dev.rafex.ether.vault.application.port.out.VaultProvisioner;
import dev.rafex.ether.vault.application.port.out.VaultRepository;
import dev.rafex.ether.vault.domain.model.Vault;

public final class VaultService implements CreateVaultUseCase, GetVaultUseCase {

    private final VaultRepository repository;
    private final VaultProvisioner provisioner;
    private final String homeRoot;

    public VaultService(final VaultRepository repository, final VaultProvisioner provisioner,
            final String homeRoot) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.provisioner = Objects.requireNonNull(provisioner, "provisioner");
        this.homeRoot = Objects.requireNonNull(homeRoot, "homeRoot");
    }

    @Override
    public synchronized Vault create(final CreateVaultCommand command) {
        Objects.requireNonNull(command, "command");
        final var name = command.name() == null ? "" : command.name().trim();
        if (repository.existsByName(name)) {
            throw new VaultAlreadyExistsException(name);
        }

        final var vault = Vault.create(name, command.recipient(), command.description(), homeRoot);
        provisioner.provision(vault);
        try {
            return repository.save(vault.ready());
        } catch (final RuntimeException failure) {
            provisioner.cleanup(vault);
            throw failure;
        }
    }

    @Override
    public Optional<Vault> get(final UUID id) {
        return repository.findById(Objects.requireNonNull(id, "id"));
    }

    public static final class VaultAlreadyExistsException extends RuntimeException {
        public VaultAlreadyExistsException(final String name) {
            super("vault already exists: " + name);
        }
    }
}
