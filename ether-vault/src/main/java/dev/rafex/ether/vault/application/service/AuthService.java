package dev.rafex.ether.vault.application.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import dev.rafex.ether.vault.application.port.in.AuthenticationUseCase;
import dev.rafex.ether.vault.application.port.out.AuthRepository;
import dev.rafex.ether.vault.domain.model.AuthPrincipal;
import dev.rafex.ether.vault.domain.model.AuthUser;

public final class AuthService implements AuthenticationUseCase {

    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private final AuthRepository repository;
    private final Duration tokenTtl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(final AuthRepository repository, final Duration tokenTtl) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tokenTtl = Objects.requireNonNull(tokenTtl, "tokenTtl");
        if (tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalArgumentException("tokenTtl must be positive");
        }
    }

    public void ensureBootstrapUser(final String username, final String password, final Set<String> scopes) {
        final var normalized = required(username, "username");
        if (repository.findUserByUsername(normalized).isEmpty()) {
            repository.saveUser(new AuthUser(UUID.randomUUID(), normalized, hashPassword(password), scopes, true,
                    Instant.now()));
        }
    }

    @Override
    public AuthToken login(final String username, final String password) {
        final var user = repository.findUserByUsername(required(username, "username"))
                .filter(AuthUser::enabled)
                .filter(value -> verifyPassword(password, value.passwordHash()))
                .orElseThrow(() -> new AuthenticationException("invalid credentials"));
        final var token = randomToken();
        final var expiresAt = Instant.now().plus(tokenTtl);
        repository.saveToken(user.id(), sha256(token), expiresAt);
        return new AuthToken(token, expiresAt, user.scopes());
    }

    public AuthUser createUser(final AuthPrincipal actor, final String username, final String password,
            final Set<String> scopes) {
        if (actor == null || !actor.hasScope("auth:admin")) {
            throw new AuthorizationException();
        }
        final var user = new AuthUser(UUID.randomUUID(), required(username, "username"), hashPassword(password),
                Objects.requireNonNull(scopes, "scopes"), true, Instant.now());
        return repository.saveUser(user);
    }

    @Override
    public Optional<AuthPrincipal> authenticate(final String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return repository.findPrincipalByTokenHash(sha256(token), Instant.now());
    }

    private String randomToken() {
        final var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashPassword(final String password) {
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("password must contain at least 12 characters");
        }
        final var salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        final var derived = derive(password, salt, PBKDF2_ITERATIONS, HASH_BYTES);
        return "pbkdf2-sha256$" + PBKDF2_ITERATIONS + "$" + encode(salt) + "$" + encode(derived);
    }

    private boolean verifyPassword(final String password, final String encoded) {
        if (password == null || encoded == null) {
            return false;
        }
        try {
            final var parts = encoded.split("\\$", -1);
            if (parts.length != 4 || !"pbkdf2-sha256".equals(parts[0])) {
                return false;
            }
            final var iterations = Integer.parseInt(parts[1]);
            final var expected = Base64.getUrlDecoder().decode(parts[3]);
            final var actual = derive(password, Base64.getUrlDecoder().decode(parts[2]), iterations, expected.length);
            return MessageDigest.isEqual(actual, expected);
        } catch (final RuntimeException e) {
            return false;
        }
    }

    private static byte[] derive(final String password, final byte[] salt, final int iterations, final int bytes) {
        final var spec = new PBEKeySpec(password.toCharArray(), salt, iterations, bytes * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static String sha256(final String value) {
        try {
            return encode(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String encode(final byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String required(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public static final class AuthenticationException extends RuntimeException {
        public AuthenticationException(final String message) {
            super(message);
        }
    }

    public static final class AuthorizationException extends RuntimeException {
        public AuthorizationException() {
            super("auth:admin scope required");
        }
    }
}
