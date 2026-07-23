package dev.rafex.ether.vault.application.port.out;

import java.util.Optional;
import java.util.UUID;

import dev.rafex.ether.vault.domain.model.LogicalNamespace;
import dev.rafex.ether.vault.domain.model.Role;

public interface AccessRepository {
    LogicalNamespace saveNamespace(LogicalNamespace namespace);

    Optional<LogicalNamespace> findNamespaceById(UUID namespaceId);

    boolean existsNamespaceByName(String name);

    void attachVault(UUID namespaceId, UUID vaultId);

    boolean vaultBelongsToNamespace(UUID namespaceId, UUID vaultId);

    void grantNamespaceRole(UUID namespaceId, UUID userId, Role role);

    void grantVaultRole(UUID vaultId, UUID userId, Role role);

    Optional<Role> findNamespaceRole(UUID namespaceId, UUID userId);

    Optional<Role> findVaultRole(UUID vaultId, UUID userId);
}
