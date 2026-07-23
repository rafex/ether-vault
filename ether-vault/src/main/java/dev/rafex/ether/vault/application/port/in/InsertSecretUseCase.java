package dev.rafex.ether.vault.application.port.in;

import java.util.UUID;

import dev.rafex.ether.vault.domain.model.SecretReference;

public interface InsertSecretUseCase {
    SecretReference insert(InsertSecretCommand command);

    record InsertSecretCommand(UUID vaultId, String namespace, String shortName, String friendlyName, String path,
            String value) {
    }
}
