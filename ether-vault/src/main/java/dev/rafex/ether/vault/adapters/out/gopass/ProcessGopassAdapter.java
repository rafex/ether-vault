package dev.rafex.ether.vault.adapters.out.gopass;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import dev.rafex.ether.vault.application.port.out.SecretStore;
import dev.rafex.ether.vault.application.port.out.VaultProvisioner;
import dev.rafex.ether.vault.domain.model.Vault;

public final class ProcessGopassAdapter implements VaultProvisioner, SecretStore {

    private static final long TIMEOUT_SECONDS = 30;
    private final String binary;
    private final String gpgHome;

    public ProcessGopassAdapter(final String binary, final String gpgHome) {
        this.binary = Objects.requireNonNull(binary, "binary");
        this.gpgHome = gpgHome;
    }

    @Override
    public void provision(final Vault vault) {
        final var result = run(java.util.List.of(binary, "--yes", "init", "--crypto", "gpgcli", "--storage",
                "gitfs", vault.recipient()), home(vault.id(), vault.homePath()), null);
        if (result.exitCode() != 0) {
            throw new GopassProvisioningException("gopass could not initialize the vault (exit " + result.exitCode()
                    + "): " + sanitize(result.output()));
        }
    }

    @Override
    public void put(final java.util.UUID vaultId, final String vaultHomeRoot, final String path, final String value) {
        final var content = value.endsWith("\n") ? value : value + "\n";
        final var result = run(java.util.List.of(binary, "insert", "--force", path), home(vaultId, vaultHomeRoot),
                content.getBytes(StandardCharsets.UTF_8));
        if (result.exitCode() != 0) {
            throw new SecretStore.SecretStoreException("gopass could not insert the secret: "
                    + sanitize(result.output()));
        }
    }

    @Override
    public String get(final java.util.UUID vaultId, final String vaultHomeRoot, final String path) {
        final var result = run(java.util.List.of(binary, "show", "--unsafe", "--noparsing", path),
                home(vaultId, vaultHomeRoot), null);
        if (result.exitCode() != 0) {
            final var output = result.output().toLowerCase(java.util.Locale.ROOT);
            if (output.contains("not in the password store") || output.contains("not found")) {
                throw new SecretStore.SecretNotFoundException(path);
            }
            throw new SecretStore.SecretStoreException("gopass could not read the secret: " + sanitize(result.output()));
        }
        return stripTrailingLineEnding(result.output());
    }

    @Override
    public void cleanup(final Vault vault) {
        final var home = Path.of(vault.homePath()).resolve(vault.id().toString());
        try {
            if (Files.exists(home)) {
                try (var paths = Files.walk(home)) {
                    paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (final IOException ignored) {
                            // Best-effort rollback; the metadata transaction remains authoritative.
                        }
                    });
                }
            }
        } catch (final IOException ignored) {
            // Best-effort rollback.
        }
    }

    private CommandResult run(final java.util.List<String> command, final Path home, final byte[] stdin) {
        try {
            Files.createDirectories(home);
            restrict(home);
            final var process = new ProcessBuilder(command).directory(home.toFile()).redirectErrorStream(true);
            final Map<String, String> environment = process.environment();
            environment.put("HOME", home.toString());
            if (gpgHome != null && !gpgHome.isBlank()) {
                environment.put("GNUPGHOME", gpgHome);
            }
            final var child = process.start();
            try (var input = child.getOutputStream()) {
                if (stdin != null) {
                    input.write(stdin);
                }
            }
            final var output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!child.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                child.destroyForcibly();
                throw new GopassProvisioningException("gopass timed out");
            }
            return new CommandResult(child.exitValue(), output);
        } catch (final IOException e) {
            throw new SecretStore.SecretStoreException("could not execute gopass", e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SecretStore.SecretStoreException("gopass operation interrupted", e);
        }
    }

    private static Path home(final java.util.UUID vaultId, final String vaultHomeRoot) {
        return Path.of(vaultHomeRoot).resolve(vaultId.toString()).toAbsolutePath().normalize();
    }

    private static String stripTrailingLineEnding(final String output) {
        if (output.endsWith("\r\n")) {
            return output.substring(0, output.length() - 2);
        }
        return output.endsWith("\n") ? output.substring(0, output.length() - 1) : output;
    }

    private record CommandResult(int exitCode, String output) {
    }

    private static void restrict(final Path path) {
        try {
            Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        } catch (final UnsupportedOperationException | IOException ignored) {
            // Windows and non-POSIX filesystems do not expose these permissions.
        }
    }

    private static String sanitize(final String output) {
        final var oneLine = output == null ? "" : output.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 512 ? oneLine.substring(0, 512) : oneLine;
    }

    public static final class GopassProvisioningException extends RuntimeException {
        public GopassProvisioningException(final String message) {
            super(message);
        }

        public GopassProvisioningException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
