package dev.rafex.ether.vault.application.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import dev.rafex.ether.vault.application.port.in.CreateNamespaceUseCase;
import dev.rafex.ether.vault.application.port.in.ListNamespaceUseCase;
import dev.rafex.ether.vault.application.port.out.SecretCatalog;
import dev.rafex.ether.vault.application.port.out.VaultRepository;
import dev.rafex.ether.vault.domain.model.SecretNamespace;

public final class NamespaceService implements CreateNamespaceUseCase, ListNamespaceUseCase {

    private final VaultRepository vaults;
    private final SecretCatalog catalog;

    public NamespaceService(final VaultRepository vaults, final SecretCatalog catalog) {
        this.vaults = Objects.requireNonNull(vaults, "vaults");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public SecretNamespace create(final UUID vaultId, final String path) {
        requireVault(vaultId);
        return catalog.ensureNamespace(vaultId, path);
    }

    @Override
    public List<SecretNamespace> list(final UUID vaultId) {
        requireVault(vaultId);
        return catalog.listNamespaces(vaultId);
    }

    private void requireVault(final UUID vaultId) {
        vaults.findById(Objects.requireNonNull(vaultId, "vaultId"))
                .orElseThrow(() -> new SecretService.VaultNotFoundException(vaultId));
    }
}
