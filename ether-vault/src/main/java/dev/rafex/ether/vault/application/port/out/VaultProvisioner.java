package dev.rafex.ether.vault.application.port.out;

import dev.rafex.ether.vault.domain.model.Vault;

public interface VaultProvisioner {
    void provision(Vault vault);

    void cleanup(Vault vault);
}
