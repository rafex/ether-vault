package dev.rafex.ether.vault.application.service;

import java.util.Objects;
import java.util.UUID;

import dev.rafex.ether.vault.application.port.out.AccessRepository;
import dev.rafex.ether.vault.domain.model.AuthPrincipal;
import dev.rafex.ether.vault.domain.model.Role;

public final class AuthorizationService {

    private final AccessRepository access;

    public AuthorizationService(final AccessRepository access) {
        this.access = Objects.requireNonNull(access, "access");
    }

    public void require(final AuthPrincipal principal, final UUID namespaceId, final UUID vaultId,
            final Role required) {
        Objects.requireNonNull(principal, "principal");
        if (!access.vaultBelongsToNamespace(namespaceId, vaultId)) {
            throw new AccessDeniedException();
        }
        final var role = access.findVaultRole(vaultId, principal.userId())
                .or(() -> access.findNamespaceRole(namespaceId, principal.userId()));
        if (role.isEmpty() || !role.orElseThrow().allows(required)) {
            throw new AccessDeniedException();
        }
    }

    public void requireNamespace(final AuthPrincipal principal, final UUID namespaceId, final Role required) {
        Objects.requireNonNull(principal, "principal");
        final var role = access.findNamespaceRole(namespaceId, principal.userId());
        if (role.isEmpty() || !role.orElseThrow().allows(required)) {
            throw new AccessDeniedException();
        }
    }

    public static final class AccessDeniedException extends RuntimeException {
        public AccessDeniedException() {
            super("insufficient role for resource");
        }
    }
}
