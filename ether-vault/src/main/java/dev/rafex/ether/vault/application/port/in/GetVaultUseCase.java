package dev.rafex.ether.vault.application.port.in;

import java.util.UUID;
import java.util.Optional;

import dev.rafex.ether.vault.domain.model.Vault;

public interface GetVaultUseCase {
    Optional<Vault> get(UUID id);
}
