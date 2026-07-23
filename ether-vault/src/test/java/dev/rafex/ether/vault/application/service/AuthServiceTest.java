package dev.rafex.ether.vault.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.rafex.ether.vault.adapters.out.persistence.SqliteAuthRepository;

class AuthServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void issuesOpaqueTokenAndAuthenticatesItFromSqlite() {
        final var repository = new SqliteAuthRepository(tempDirectory.resolve("auth.sqlite"));
        final var service = new AuthService(repository, Duration.ofMinutes(10));
        service.ensureBootstrapUser("admin", "test-password-123", Set.of("vault:read", "vault:write"));

        final var token = service.login("admin", "test-password-123");
        final var principal = service.authenticate(token.accessToken()).orElseThrow();

        assertTrue(token.accessToken().length() > 30);
        assertEquals("admin", principal.username());
        assertTrue(principal.hasScope("vault:read"));
        assertTrue(service.authenticate("wrong-token").isEmpty());
    }

    @Test
    void rejectsInvalidCredentials() {
        final var repository = new SqliteAuthRepository(tempDirectory.resolve("auth.sqlite"));
        final var service = new AuthService(repository, Duration.ofMinutes(10));
        service.ensureBootstrapUser("admin", "test-password-123", Set.of("vault:read"));

        assertThrows(AuthService.AuthenticationException.class, () -> service.login("admin", "wrong-password"));
    }
}
