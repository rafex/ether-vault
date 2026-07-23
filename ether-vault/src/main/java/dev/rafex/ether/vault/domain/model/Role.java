package dev.rafex.ether.vault.domain.model;

public enum Role {
    READ(1),
    WRITE(2),
    OWNER(3);

    private final int rank;

    Role(final int rank) {
        this.rank = rank;
    }

    public boolean allows(final Role required) {
        return rank >= required.rank;
    }
}
