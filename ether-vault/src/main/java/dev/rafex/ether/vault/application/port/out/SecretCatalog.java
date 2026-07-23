package dev.rafex.ether.vault.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.rafex.ether.vault.domain.model.SecretNamespace;
import dev.rafex.ether.vault.domain.model.SecretReference;

public interface SecretCatalog {
    SecretNamespace ensureNamespace(UUID vaultId, String path);

    List<SecretNamespace> listNamespaces(UUID vaultId);

    SecretReference save(SecretReference reference);

    Optional<SecretReference> findById(UUID vaultId, UUID id);

    Optional<SecretReference> findByPath(UUID vaultId, String path);

    List<SecretReference> findByShortName(UUID vaultId, String shortName);

    List<SecretReference> findByFriendlyName(UUID vaultId, String friendlyName);
}
