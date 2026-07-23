package dev.rafex.ether.vault.application.port.in;

import java.util.List;
import java.util.UUID;

import dev.rafex.ether.vault.domain.model.SecretNamespace;

public interface ListNamespaceUseCase {
    List<SecretNamespace> list(UUID vaultId);
}
