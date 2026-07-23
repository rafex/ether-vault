package dev.rafex.ether.vault.adapters.in.http;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.ether.http.jetty12.handler.ResourceHandler;
import dev.rafex.ether.json.JsonCodec;
import dev.rafex.ether.vault.application.port.in.InsertSecretUseCase;
import dev.rafex.ether.vault.application.port.in.ReadSecretUseCase;
import dev.rafex.ether.vault.application.port.out.SecretStore;
import dev.rafex.ether.vault.application.service.AuthService;
import dev.rafex.ether.vault.application.service.AuthorizationService;
import dev.rafex.ether.vault.application.service.SecretService;
import dev.rafex.ether.vault.domain.model.AuthPrincipal;
import dev.rafex.ether.vault.domain.model.Role;
import dev.rafex.ether.vault.domain.model.SecretReference;

public final class VaultHttpResource extends ResourceHandler {

    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private final InsertSecretUseCase insertSecret;
    private final ReadSecretUseCase readSecret;
    private final JsonCodec json;
    private final AuthService authentication;
    private final AuthorizationService authorization;

    public VaultHttpResource(final InsertSecretUseCase insertSecret, final ReadSecretUseCase readSecret,
            final JsonCodec json, final AuthService authentication, final AuthorizationService authorization) {
        super(json);
        this.insertSecret = insertSecret;
        this.readSecret = readSecret;
        this.json = json;
        this.authentication = authentication;
        this.authorization = authorization;
    }

    @Override
    protected String basePath() {
        return "/api/v1/vaults";
    }

    @Override
    protected List<Route> routes() {
        return List.of(Route.of("/{namespaceId}/namespaces/{vaultId}/secrets", Set.of("GET", "POST")),
                Route.of("/{namespaceId}/namespaces/{vaultId}/secrets/{secretId}", Set.of("GET")),
                Route.of("/{namespaceId}/namespaces/{vaultId}/secrets/{secretId}/", Set.of("GET")));
    }

    @Override
    public boolean post(final HttpExchange exchange) {
        final var principal = principal(exchange);
        if (principal.isEmpty()) {
            exchange.json(401, Map.of("error", "unauthorized"));
            return true;
        }
        try {
            final var namespaceId = UUID.fromString(exchange.pathParam("namespaceId"));
            final var vaultId = UUID.fromString(exchange.pathParam("vaultId"));
            authorization.require(principal.orElseThrow(), namespaceId, vaultId, Role.WRITE);
            final var request = readRequest(exchange);
            final var reference = insertSecret.insert(new InsertSecretUseCase.InsertSecretCommand(vaultId,
                    nullableText(request, "namespace"), nullableText(request, "shortName"),
                    text(request, "friendlyName"), nullableText(request, "path"), text(request, "value")));
            exchange.json(201, SecretReferenceView.from(reference));
        } catch (final AuthorizationService.AccessDeniedException e) {
            exchange.json(403, Map.of("error", "forbidden"));
        } catch (final SecretService.SecretAlreadyExistsException e) {
            exchange.json(409, Map.of("error", "secret_already_exists", "message", e.getMessage()));
        } catch (final IllegalArgumentException e) {
            exchange.json(422, Map.of("error", "invalid_request", "message", e.getMessage()));
        } catch (final SecretStore.SecretStoreException e) {
            exchange.json(502, Map.of("error", "secret_store_unavailable", "message", e.getMessage()));
        }
        return true;
    }

    @Override
    public boolean get(final HttpExchange exchange) {
        final var principal = principal(exchange);
        if (principal.isEmpty()) {
            exchange.json(401, Map.of("error", "unauthorized"));
            return true;
        }
        try {
            final var namespaceId = UUID.fromString(exchange.pathParam("namespaceId"));
            final var vaultId = UUID.fromString(exchange.pathParam("vaultId"));
            authorization.require(principal.orElseThrow(), namespaceId, vaultId, Role.READ);
            final var selector = isDirectSecretRoute(exchange)
                    ? new ReadSecretUseCase.SecretSelector(UUID.fromString(exchange.pathParam("secretId")), null, null,
                            null)
                    : new ReadSecretUseCase.SecretSelector(uuidQuery(exchange, "secretId"),
                            query(exchange, "shortName"), query(exchange, "friendlyName"), query(exchange, "path"));
            final var read = readSecret.read(vaultId, selector);
            if (exchange instanceof JettyHttpExchange jetty) {
                jetty.response().getHeaders().put("Cache-Control", "no-store");
            }
            exchange.json(200, SecretView.from(read.reference(), read.value()));
        } catch (final AuthorizationService.AccessDeniedException e) {
            exchange.json(403, Map.of("error", "forbidden"));
        } catch (final SecretService.SecretNotFoundException | SecretStore.SecretNotFoundException e) {
            exchange.json(404, Map.of("error", "secret_not_found"));
        } catch (final SecretService.AmbiguousSecretException e) {
            exchange.json(409, Map.of("error", "ambiguous_secret_selector"));
        } catch (final IllegalArgumentException e) {
            exchange.json(422, Map.of("error", "invalid_request", "message", e.getMessage()));
        } catch (final SecretStore.SecretStoreException e) {
            exchange.json(502, Map.of("error", "secret_store_unavailable", "message", e.getMessage()));
        }
        return true;
    }

    private Optional<AuthPrincipal> principal(final HttpExchange exchange) {
        if (!(exchange instanceof JettyHttpExchange jetty)) {
            return Optional.empty();
        }
        final var header = jetty.request().getHeaders().get("Authorization");
        final var token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        return authentication.authenticate(token);
    }

    private JsonNode readRequest(final HttpExchange exchange) {
        if (!(exchange instanceof JettyHttpExchange jetty)) {
            throw new IllegalStateException("request body is not available for this transport");
        }
        try {
            final var body = org.eclipse.jetty.server.Request.asInputStream(jetty.request()).readAllBytes();
            if (body.length == 0 || body.length > MAX_BODY_BYTES) {
                throw new IllegalArgumentException("request body is invalid");
            }
            return json.readTree(body);
        } catch (final IOException e) {
            throw new IllegalArgumentException("could not read request body", e);
        }
    }

    private static boolean isDirectSecretRoute(final HttpExchange exchange) {
        return exchange.path() != null && exchange.path().contains("/secrets/");
    }

    private static String text(final JsonNode request, final String field) {
        final var value = nullableText(request, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String nullableText(final JsonNode request, final String field) {
        final var node = request == null ? null : request.get(field);
        return node == null || node.isNull() ? null : node.asText(null);
    }

    private static String query(final HttpExchange exchange, final String name) {
        final var value = exchange.queryFirst(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static UUID uuidQuery(final HttpExchange exchange, final String name) {
        final var value = query(exchange, name);
        return value == null ? null : UUID.fromString(value);
    }

    private record SecretReferenceView(String id, String vaultId, String folderId, String shortName,
            String friendlyName, String path, String createdAt, String updatedAt) {
        static SecretReferenceView from(final SecretReference reference) {
            return new SecretReferenceView(reference.id().toString(), reference.vaultId().toString(),
                    reference.namespaceId().toString(), reference.shortName(), reference.friendlyName(),
                    reference.path(), reference.createdAt().toString(), reference.updatedAt().toString());
        }
    }

    private record SecretView(String id, String vaultId, String folderId, String shortName, String friendlyName,
            String path, String value) {
        static SecretView from(final SecretReference reference, final String value) {
            return new SecretView(reference.id().toString(), reference.vaultId().toString(),
                    reference.namespaceId().toString(), reference.shortName(), reference.friendlyName(),
                    reference.path(), value);
        }
    }
}
