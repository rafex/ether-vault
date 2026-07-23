package dev.rafex.ether.vault.application.service;

import java.util.Objects;
import java.util.UUID;

import dev.rafex.ether.vault.application.port.out.AccessRepository;
import dev.rafex.ether.vault.domain.model.AuthPrincipal;
import dev.rafex.ether.vault.domain.model.LogicalNamespace;
import dev.rafex.ether.vault.domain.model.Role;

public final class LogicalNamespaceService {

    private final AccessRepository access;

    public LogicalNamespaceService(final AccessRepository access) {
        this.access = Objects.requireNonNull(access, "access");
    }

    public LogicalNamespace create(final AuthPrincipal owner, final String name, final String description) {
        Objects.requireNonNull(owner, "owner");
        final var namespace = LogicalNamespace.create(name, description);
        if (access.existsNamespaceByName(namespace.name())) {
            throw new NamespaceAlreadyExistsException(namespace.name());
        }
        access.saveNamespace(namespace);
        access.grantNamespaceRole(namespace.id(), owner.userId(), Role.OWNER);
        return namespace;
    }

    public LogicalNamespace get(final UUID namespaceId) {
        return access.findNamespaceById(namespaceId)
                .orElseThrow(() -> new NamespaceNotFoundException(namespaceId));
    }

    public void attachVault(final AuthPrincipal owner, final UUID namespaceId, final UUID vaultId) {
        access.attachVault(namespaceId, vaultId);
        access.grantVaultRole(vaultId, owner.userId(), Role.OWNER);
    }

    public void grantNamespaceRole(final AuthPrincipal owner, final UUID namespaceId, final UUID userId,
            final Role role) {
        access.grantNamespaceRole(namespaceId, userId, role);
    }

    public void grantVaultRole(final AuthPrincipal owner, final UUID namespaceId, final UUID vaultId,
            final UUID userId, final Role role) {
        access.grantVaultRole(vaultId, userId, role);
    }

    public static final class NamespaceAlreadyExistsException extends RuntimeException {
        public NamespaceAlreadyExistsException(final String name) {
            super("namespace already exists: " + name);
        }
    }

    public static final class NamespaceNotFoundException extends RuntimeException {
        public NamespaceNotFoundException(final UUID id) {
            super("namespace not found: " + id);
        }
    }
}
