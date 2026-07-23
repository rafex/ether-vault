package dev.rafex.ether.vault.application.port.in;

import java.util.UUID;

import dev.rafex.ether.vault.domain.model.SecretNamespace;

public interface CreateNamespaceUseCase {
    SecretNamespace create(UUID vaultId, String path);
}
