package dev.rafex.ether.vault.adapters.out.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

import dev.rafex.ether.vault.application.port.out.VaultRepository;
import dev.rafex.ether.vault.domain.model.Vault;
import dev.rafex.ether.vault.domain.model.VaultStatus;

public final class SqliteVaultRepository implements VaultRepository {

    private final String jdbcUrl;

    public SqliteVaultRepository(final Path databasePath) {
        try {
            final var parent = databasePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
            initialize();
        } catch (final Exception e) {
            throw new IllegalStateException("could not initialize sqlite database", e);
        }
    }

    @Override
    public synchronized Vault save(final Vault vault) {
        final var sql = "INSERT INTO vaults (id, name, recipient, description, home_path, status, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (var connection = open(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, vault.id().toString());
            statement.setString(2, vault.name());
            statement.setString(3, vault.recipient());
            statement.setString(4, vault.description());
            statement.setString(5, vault.homePath());
            statement.setString(6, vault.status().name());
            statement.setString(7, vault.createdAt().toString());
            statement.executeUpdate();
            return vault;
        } catch (final SQLException e) {
            throw new IllegalStateException("could not save vault", e);
        }
    }

    @Override
    public synchronized Optional<Vault> findById(final UUID id) {
        try (var connection = open(); var statement = connection.prepareStatement(
                "SELECT id, name, recipient, description, home_path, status, created_at FROM vaults WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("could not read vault", e);
        }
    }

    @Override
    public synchronized boolean existsByName(final String name) {
        try (var connection = open(); var statement = connection.prepareStatement(
                "SELECT 1 FROM vaults WHERE name = ? LIMIT 1")) {
            statement.setString(1, name);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("could not check vault name", e);
        }
    }

    private void initialize() throws SQLException {
        try (var connection = open(); var statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS vaults (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL UNIQUE,
                        recipient TEXT NOT NULL,
                        description TEXT NOT NULL,
                        home_path TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private static Vault map(final java.sql.ResultSet result) throws SQLException {
        return new Vault(UUID.fromString(result.getString("id")), result.getString("name"),
                result.getString("recipient"), result.getString("description"), result.getString("home_path"),
                VaultStatus.valueOf(result.getString("status")),
                java.time.Instant.parse(result.getString("created_at")));
    }
}
