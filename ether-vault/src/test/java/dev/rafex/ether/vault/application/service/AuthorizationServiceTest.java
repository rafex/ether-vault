package dev.rafex.ether.vault.application.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.rafex.ether.vault.application.port.out.AccessRepository;
import dev.rafex.ether.vault.domain.model.AuthPrincipal;
import dev.rafex.ether.vault.domain.model.LogicalNamespace;
import dev.rafex.ether.vault.domain.model.Role;

class AuthorizationServiceTest {

    @Test
    void inheritsNamespaceRoleAndAllowsVaultOverride() {
        final var repository = new FakeAccessRepository();
        final var namespaceId = UUID.randomUUID();
        final var vaultId = UUID.randomUUID();
        final var userId = UUID.randomUUID();
        repository.attach(namespaceId, vaultId);
        repository.namespaceRoles.put(namespaceId + ":" + userId, Role.WRITE);
        final var authorization = new AuthorizationService(repository);
        final var principal = new AuthPrincipal(userId, "writer", Set.of(), Instant.now().plusSeconds(60));

        assertDoesNotThrow(() -> authorization.require(principal, namespaceId, vaultId, Role.READ));
        assertDoesNotThrow(() -> authorization.require(principal, namespaceId, vaultId, Role.WRITE));
        assertThrows(AuthorizationService.AccessDeniedException.class,
                () -> authorization.require(principal, namespaceId, vaultId, Role.OWNER));

        repository.vaultRoles.put(vaultId + ":" + userId, Role.READ);
        assertThrows(AuthorizationService.AccessDeniedException.class,
                () -> authorization.require(principal, namespaceId, vaultId, Role.WRITE));
    }

    private static final class FakeAccessRepository implements AccessRepository {
        private final Map<String, Role> namespaceRoles = new HashMap<>();
        private final Map<String, Role> vaultRoles = new HashMap<>();
        private String relation;

        private void attach(final UUID namespaceId, final UUID vaultId) {
            relation = namespaceId + ":" + vaultId;
        }

        @Override
        public LogicalNamespace saveNamespace(final LogicalNamespace namespace) {
            return namespace;
        }

        @Override
        public Optional<LogicalNamespace> findNamespaceById(final UUID namespaceId) {
            return Optional.empty();
        }

        @Override
        public boolean existsNamespaceByName(final String name) {
            return false;
        }

        @Override
        public void attachVault(final UUID namespaceId, final UUID vaultId) {
            attach(namespaceId, vaultId);
        }

        @Override
        public boolean vaultBelongsToNamespace(final UUID namespaceId, final UUID vaultId) {
            return relation.equals(namespaceId + ":" + vaultId);
        }

        @Override
        public void grantNamespaceRole(final UUID namespaceId, final UUID userId, final Role role) {
            namespaceRoles.put(namespaceId + ":" + userId, role);
        }

        @Override
        public void grantVaultRole(final UUID vaultId, final UUID userId, final Role role) {
            vaultRoles.put(vaultId + ":" + userId, role);
        }

        @Override
        public Optional<Role> findNamespaceRole(final UUID namespaceId, final UUID userId) {
            return Optional.ofNullable(namespaceRoles.get(namespaceId + ":" + userId));
        }

        @Override
        public Optional<Role> findVaultRole(final UUID vaultId, final UUID userId) {
            return Optional.ofNullable(vaultRoles.get(vaultId + ":" + userId));
        }
    }
}
