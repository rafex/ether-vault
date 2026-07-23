package dev.rafex.ether.vault.application.port.in;

import java.util.UUID;

import dev.rafex.ether.vault.domain.model.SecretReference;

public interface ReadSecretUseCase {
    SecretRead read(UUID vaultId, SecretSelector selector);

    record SecretSelector(UUID id, String shortName, String friendlyName, String path) {
        public int populatedFields() {
            return (id == null ? 0 : 1) + (shortName == null ? 0 : 1) + (friendlyName == null ? 0 : 1)
                    + (path == null ? 0 : 1);
        }
    }

    record SecretRead(SecretReference reference, String value) {
    }
}
