package dev.rafex.ether.vault.application.port.out;

import java.util.UUID;

public interface SecretStore {
    void put(UUID vaultId, String vaultHomeRoot, String path, String value);

    String get(UUID vaultId, String vaultHomeRoot, String path);

    final class SecretNotFoundException extends RuntimeException {
        public SecretNotFoundException(final String path) {
            super("secret not found: " + path);
        }
    }

    final class SecretStoreException extends RuntimeException {
        public SecretStoreException(final String message) {
            super(message);
        }

        public SecretStoreException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
