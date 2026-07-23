package dev.rafex.ether.vault.domain.model;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record Secret(UUID vaultId, String path, String value) {

    private static final Pattern PATH = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._-]{0,63}(?:/[A-Za-z0-9][A-Za-z0-9._-]{0,63}){0,15}");

    public Secret {
        Objects.requireNonNull(vaultId, "vaultId");
        path = validatePath(path);
        Objects.requireNonNull(value, "value");
    }

    public static String validatePath(final String value) {
        if (value == null || value.isBlank() || !PATH.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("path must contain safe vault segments separated by '/'");
        }
        return value.trim();
    }

    public static String validateSegment(final String value, final String field) {
        if (value == null || value.isBlank() || !value.trim().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException(field + " must be a safe vault segment");
        }
        return value.trim();
    }
}
