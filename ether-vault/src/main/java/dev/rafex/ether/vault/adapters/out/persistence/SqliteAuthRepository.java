package dev.rafex.ether.vault.adapters.out.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import dev.rafex.ether.vault.application.port.out.AuthRepository;
import dev.rafex.ether.vault.domain.model.AuthPrincipal;
import dev.rafex.ether.vault.domain.model.AuthUser;

public final class SqliteAuthRepository implements AuthRepository {

    private final String jdbcUrl;

    public SqliteAuthRepository(final Path databasePath) {
        try {
            final var parent = databasePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
            initialize();
        } catch (final Exception e) {
            throw new IllegalStateException("could not initialize auth database", e);
        }
    }

    @Override
    public synchronized java.util.Optional<AuthUser> findUserByUsername(final String username) {
        try (var connection = open(); var statement = connection.prepareStatement(
                "SELECT id, username, password_hash, scopes, enabled, created_at FROM auth_users WHERE username = ?")) {
            statement.setString(1, username);
            try (var result = statement.executeQuery()) {
                return result.next() ? java.util.Optional.of(mapUser(result)) : java.util.Optional.empty();
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("could not find auth user", e);
        }
    }

    @Override
    public synchronized AuthUser saveUser(final AuthUser user) {
        try (var connection = open(); var statement = connection.prepareStatement("""
                INSERT INTO auth_users (id, username, password_hash, scopes, enabled, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, user.id().toString());
            statement.setString(2, user.username());
            statement.setString(3, user.passwordHash());
            statement.setString(4, String.join(" ", user.scopes()));
            statement.setBoolean(5, user.enabled());
            statement.setString(6, user.createdAt().toString());
            statement.executeUpdate();
            return user;
        } catch (final SQLException e) {
            throw new IllegalStateException("could not save auth user", e);
        }
    }

    @Override
    public synchronized java.util.Optional<AuthPrincipal> findPrincipalByTokenHash(final String tokenHash,
            final Instant now) {
        try (var connection = open(); var statement = connection.prepareStatement("""
                SELECT u.id, u.username, u.scopes, t.expires_at
                FROM auth_tokens t JOIN auth_users u ON u.id = t.user_id
                WHERE t.token_hash = ? AND t.expires_at > ? AND u.enabled = 1
                """)) {
            statement.setString(1, tokenHash);
            statement.setString(2, now.toString());
            try (var result = statement.executeQuery()) {
                return result.next() ? java.util.Optional.of(new AuthPrincipal(UUID.fromString(result.getString("id")),
                        result.getString("username"), scopes(result.getString("scopes")),
                        Instant.parse(result.getString("expires_at")))) : java.util.Optional.empty();
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("could not authenticate token", e);
        }
    }

    @Override
    public synchronized void saveToken(final UUID userId, final String tokenHash, final Instant expiresAt) {
        try (var connection = open(); var statement = connection.prepareStatement("""
                INSERT INTO auth_tokens (id, user_id, token_hash, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, userId.toString());
            statement.setString(3, tokenHash);
            statement.setString(4, expiresAt.toString());
            statement.setString(5, Instant.now().toString());
            statement.executeUpdate();
        } catch (final SQLException e) {
            throw new IllegalStateException("could not save auth token", e);
        }
    }

    private void initialize() throws SQLException {
        try (var connection = open(); var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS auth_users (
                        id TEXT PRIMARY KEY,
                        username TEXT NOT NULL UNIQUE,
                        password_hash TEXT NOT NULL,
                        scopes TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS auth_tokens (
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
                        token_hash TEXT NOT NULL UNIQUE,
                        expires_at TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_auth_tokens_expiry ON auth_tokens(expires_at)");
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private static AuthUser mapUser(final java.sql.ResultSet result) throws SQLException {
        return new AuthUser(UUID.fromString(result.getString("id")), result.getString("username"),
                result.getString("password_hash"), scopes(result.getString("scopes")), result.getBoolean("enabled"),
                Instant.parse(result.getString("created_at")));
    }

    private static Set<String> scopes(final String value) {
        return Set.copyOf(Arrays.stream((value == null ? "" : value).split("\\s+"))
                .filter(scope -> !scope.isBlank()).toList());
    }
}
