package dev.rafex.ether.vault.application.port.out;

import java.util.Optional;
import java.util.UUID;

import dev.rafex.ether.vault.domain.model.Vault;

public interface VaultRepository {
    Vault save(Vault vault);

    Optional<Vault> findById(UUID id);

    boolean existsByName(String name);
}
