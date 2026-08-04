package dev.rafex.ether.vault.adapters.out.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import dev.rafex.ether.vault.application.port.out.AccessRepository;
import dev.rafex.ether.vault.domain.model.LogicalNamespace;
import dev.rafex.ether.vault.domain.model.Role;

public final class SqliteAccessRepository implements AccessRepository {

    private final String jdbcUrl;

    public SqliteAccessRepository(final Path databasePath) {
        try {
            final var parent = databasePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
            initialize();
        } catch (final Exception e) {
            throw new IllegalStateException("could not initialize access database", e);
        }
    }

    @Override
    public synchronized LogicalNamespace saveNamespace(final LogicalNamespace namespace) {
        try (var connection = open(); var statement = connection.prepareStatement("""
                INSERT INTO logical_namespaces (id, name, description, created_at)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setString(1, namespace.id().toString());
            statement.setString(2, namespace.name());
            statement.setString(3, namespace.description());
            statement.setString(4, namespace.createdAt().toString());
            statement.executeUpdate();
            return namespace;
        } catch (final SQLException e) {
            throw new IllegalStateException("could not save logical namespace", e);
        }
    }

    @Override
    public synchronized Optional<LogicalNamespace> findNamespaceById(final UUID namespaceId) {
        try (var connection = open(); var statement = connection.prepareStatement(
                "SELECT id, name, description, created_at FROM logical_namespaces WHERE id = ?")) {
            statement.setString(1, namespaceId.toString());
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(new LogicalNamespace(UUID.fromString(result.getString("id")),
                        result.getString("name"), result.getString("description"),
                        Instant.parse(result.getString("created_at")))) : Optional.empty();
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("could not find logical namespace", e);
        }
    }

    @Override
    public synchronized boolean existsNamespaceByName(final String name) {
        try (var connection = open(); var statement = connection.prepareStatement(
                "SELECT 1 FROM logical_namespaces WHERE name = ? LIMIT 1")) {
            statement.setString(1, name);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("could not check logical namespace", e);
        }
    }

    @Override
    public synchronized void attachVault(final UUID namespaceId, final UUID vaultId) {
        try (var connection = open(); var statement = connection.prepareStatement(
                "INSERT INTO namespace_vaults (namespace_id, vault_id) VALUES (?, ?)")) {
            statement.setString(1, namespaceId.toString());
            statement.setString(2, vaultId.toString());
            statement.executeUpdate();
        } catch (final SQLException e) {
            throw new IllegalStateException("could not attach vault to namespace", e);
        }
    }

    @Override
    public synchronized boolean vaultBelongsToNamespace(final UUID namespaceId, final UUID vaultId) {
        try (var connection = open(); var statement = connection.prepareStatement(
                "SELECT 1 FROM namespace_vaults WHERE namespace_id = ? AND vault_id = ?")) {
            statement.setString(1, namespaceId.toString());
            statement.setString(2, vaultId.toString());
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("could not check vault namespace", e);
        }
    }

    @Override
    public synchronized void grantNamespaceRole(final UUID namespaceId, final UUID userId, final Role role) {
        upsertRole("namespace_members", "namespace_id", namespaceId, userId, role);
    }

    @Override
    public synchronized void grantVaultRole(final UUID vaultId, final UUID userId, final Role role) {
        upsertRole("vault_members", "vault_id", vaultId, userId, role);
    }

    @Override
    public synchronized Optional<Role> findNamespaceRole(final UUID namespaceId, final UUID userId) {
        return findRole("namespace_members", "namespace_id", namespaceId, userId);
    }

    @Override
    public synchronized Optional<Role> findVaultRole(final UUID vaultId, final UUID userId) {
        return findRole("vault_members", "vault_id", vaultId, userId);
    }

    private void upsertRole(final String table, final String resourceColumn, final UUID resourceId,
            final UUID userId, final Role role) {
        try (var connection = open(); var statement = connection.prepareStatement(
                "INSERT INTO " + table + " (" + resourceColumn + ", user_id, role_code) VALUES (?, ?, ?) "
                        + "ON CONFLICT(" + resourceColumn + ", user_id) DO UPDATE SET role_code = excluded.role_code")) {
            statement.setString(1, resourceId.toString());
            statement.setString(2, userId.toString());
            statement.setString(3, role.name());
            statement.executeUpdate();
        } catch (final SQLException e) {
            throw new IllegalStateException("could not grant role", e);
        }
    }

    private Optional<Role> findRole(final String table, final String resourceColumn, final UUID resourceId,
            final UUID userId) {
        try (var connection = open(); var statement = connection.prepareStatement(
                "SELECT role_code FROM " + table + " WHERE " + resourceColumn + " = ? AND user_id = ?")) {
            statement.setString(1, resourceId.toString());
            statement.setString(2, userId.toString());
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(Role.valueOf(result.getString("role_code"))) : Optional.empty();
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("could not read role", e);
        }
    }

    private void initialize() throws SQLException {
        try (var connection = open(); var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS roles (
                        code TEXT PRIMARY KEY,
                        rank INTEGER NOT NULL UNIQUE
                    )
                    """);
            statement.execute("INSERT OR IGNORE INTO roles (code, rank) VALUES ('READ', 1), ('WRITE', 2), ('OWNER', 3)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS logical_namespaces (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL UNIQUE,
                        description TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS namespace_vaults (
                        namespace_id TEXT NOT NULL REFERENCES logical_namespaces(id) ON DELETE CASCADE,
                        vault_id TEXT NOT NULL UNIQUE REFERENCES vaults(id) ON DELETE CASCADE,
                        PRIMARY KEY(namespace_id, vault_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS namespace_members (
                        namespace_id TEXT NOT NULL REFERENCES logical_namespaces(id) ON DELETE CASCADE,
                        user_id TEXT NOT NULL,
                        role_code TEXT NOT NULL REFERENCES roles(code),
                        PRIMARY KEY(namespace_id, user_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS vault_members (
                        vault_id TEXT NOT NULL REFERENCES vaults(id) ON DELETE CASCADE,
                        user_id TEXT NOT NULL,
                        role_code TEXT NOT NULL REFERENCES roles(code),
                        PRIMARY KEY(vault_id, user_id)
                    )
                    """);
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
