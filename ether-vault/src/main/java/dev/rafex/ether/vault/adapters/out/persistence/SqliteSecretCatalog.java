package dev.rafex.ether.vault.adapters.out.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.rafex.ether.vault.application.port.out.SecretCatalog;
import dev.rafex.ether.vault.domain.model.SecretNamespace;
import dev.rafex.ether.vault.domain.model.SecretReference;

public final class SqliteSecretCatalog implements SecretCatalog {

    private final String jdbcUrl;

    public SqliteSecretCatalog(final Path databasePath) {
        try {
            final var parent = databasePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
            initialize();
        } catch (final Exception e) {
            throw new IllegalStateException("could not initialize secret catalog", e);
        }
    }

    @Override
    public synchronized SecretNamespace ensureNamespace(final UUID vaultId, final String path) {
        final var normalized = path == null ? "" : path.trim();
        final var existing = findNamespace(vaultId, normalized);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        final var namespace = SecretNamespace.create(vaultId, normalized);
        try (var connection = open(); var statement = connection.prepareStatement(
                "INSERT INTO secret_namespaces (id, vault_id, path, created_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, namespace.id().toString());
            statement.setString(2, namespace.vaultId().toString());
            statement.setString(3, namespace.path());
            statement.setString(4, namespace.createdAt().toString());
            statement.executeUpdate();
            return namespace;
        } catch (final SQLException e) {
            throw new IllegalStateException("could not save namespace", e);
        }
    }

    @Override
    public synchronized List<SecretNamespace> listNamespaces(final UUID vaultId) {
        try (var connection = open(); var statement = connection.prepareStatement(
                "SELECT id, vault_id, path, created_at FROM secret_namespaces WHERE vault_id = ? ORDER BY path")) {
            statement.setString(1, vaultId.toString());
            try (var result = statement.executeQuery()) {
                final var values = new ArrayList<SecretNamespace>();
                while (result.next()) {
                    values.add(mapNamespace(result));
                }
                return values;
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("could not list namespaces", e);
        }
    }

    @Override
    public synchronized SecretReference save(final SecretReference reference) {
        try (var connection = open(); var statement = connection.prepareStatement("""
                INSERT INTO secret_references
                    (id, vault_id, namespace_id, short_name, friendly_name, path, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, reference.id().toString());
            statement.setString(2, reference.vaultId().toString());
            statement.setString(3, reference.namespaceId().toString());
            statement.setString(4, reference.shortName());
            statement.setString(5, reference.friendlyName());
            statement.setString(6, reference.path());
            statement.setString(7, reference.createdAt().toString());
            statement.setString(8, reference.updatedAt().toString());
            statement.executeUpdate();
            return reference;
        } catch (final SQLException e) {
            throw new IllegalStateException("could not save secret reference", e);
        }
    }

    @Override
    public synchronized Optional<SecretReference> findById(final UUID vaultId, final UUID id) {
        return findOne("WHERE vault_id = ? AND id = ?", vaultId.toString(), id.toString());
    }

    @Override
    public synchronized Optional<SecretReference> findByPath(final UUID vaultId, final String path) {
        return findOne("WHERE vault_id = ? AND path = ?", vaultId.toString(), path);
    }

    @Override
    public synchronized List<SecretReference> findByShortName(final UUID vaultId, final String shortName) {
        return findMany("WHERE vault_id = ? AND short_name = ?", vaultId.toString(), shortName);
    }

    @Override
    public synchronized List<SecretReference> findByFriendlyName(final UUID vaultId, final String friendlyName) {
        return findMany("WHERE vault_id = ? AND friendly_name = ?", vaultId.toString(), friendlyName);
    }

    private Optional<SecretNamespace> findNamespace(final UUID vaultId, final String path) {
        try (var connection = open(); var statement = connection.prepareStatement(
                "SELECT id, vault_id, path, created_at FROM secret_namespaces WHERE vault_id = ? AND path = ?")) {
            statement.setString(1, vaultId.toString());
            statement.setString(2, path);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapNamespace(result)) : Optional.empty();
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("could not find namespace", e);
        }
    }

    private Optional<SecretReference> findOne(final String where, final String... parameters) {
        try (var connection = open(); var statement = connection.prepareStatement(
                "SELECT id, vault_id, namespace_id, short_name, friendly_name, path, created_at, updated_at "
                        + "FROM secret_references " + where)) {
            for (var index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapReference(result)) : Optional.empty();
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("could not find secret reference", e);
        }
    }

    private List<SecretReference> findMany(final String where, final String... parameters) {
        try (var connection = open(); var statement = connection.prepareStatement(
                "SELECT id, vault_id, namespace_id, short_name, friendly_name, path, created_at, updated_at "
                        + "FROM secret_references " + where + " ORDER BY path")) {
            for (var index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            try (var result = statement.executeQuery()) {
                final var values = new ArrayList<SecretReference>();
                while (result.next()) {
                    values.add(mapReference(result));
                }
                return values;
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("could not find secret references", e);
        }
    }

    private void initialize() throws SQLException {
        try (var connection = open(); var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS secret_namespaces (
                        id TEXT PRIMARY KEY,
                        vault_id TEXT NOT NULL REFERENCES vaults(id) ON DELETE CASCADE,
                        path TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        UNIQUE(vault_id, path)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS secret_references (
                        id TEXT PRIMARY KEY,
                        vault_id TEXT NOT NULL REFERENCES vaults(id) ON DELETE CASCADE,
                        namespace_id TEXT NOT NULL REFERENCES secret_namespaces(id) ON DELETE CASCADE,
                        short_name TEXT NOT NULL,
                        friendly_name TEXT NOT NULL,
                        path TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        UNIQUE(vault_id, path)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_secret_short_name ON secret_references(vault_id, short_name)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_secret_friendly_name ON secret_references(vault_id, friendly_name)");
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private static SecretNamespace mapNamespace(final java.sql.ResultSet result) throws SQLException {
        return new SecretNamespace(UUID.fromString(result.getString("id")),
                UUID.fromString(result.getString("vault_id")), result.getString("path"),
                java.time.Instant.parse(result.getString("created_at")));
    }

    private static SecretReference mapReference(final java.sql.ResultSet result) throws SQLException {
        return new SecretReference(UUID.fromString(result.getString("id")),
                UUID.fromString(result.getString("vault_id")), UUID.fromString(result.getString("namespace_id")),
                result.getString("short_name"), result.getString("friendly_name"), result.getString("path"),
                java.time.Instant.parse(result.getString("created_at")),
                java.time.Instant.parse(result.getString("updated_at")));
    }
}
