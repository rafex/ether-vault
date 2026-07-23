package dev.rafex.ether.vault.application.port.in;

import dev.rafex.ether.vault.domain.model.Vault;

public interface CreateVaultUseCase {
    Vault create(CreateVaultCommand command);

    record CreateVaultCommand(String name, String recipient, String description) {
    }
}
