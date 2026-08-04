package dev.rafex.ether.vault.adapters.in.http;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.ether.http.jetty12.handler.ResourceHandler;
import dev.rafex.ether.json.JsonCodec;
import dev.rafex.ether.vault.application.service.AuthService;
import dev.rafex.ether.vault.application.service.AuthorizationService;
import dev.rafex.ether.vault.application.service.LogicalNamespaceService;
import dev.rafex.ether.vault.application.service.VaultService;
import dev.rafex.ether.vault.domain.model.LogicalNamespace;
import dev.rafex.ether.vault.domain.model.Role;
import dev.rafex.ether.vault.domain.model.Vault;

public final class NamespaceHttpResource extends ResourceHandler {

    private static final int MAX_BODY_BYTES = 64 * 1024;
    private final LogicalNamespaceService namespaces;
    private final VaultService vaults;
    private final AuthorizationService authorization;
    private final AuthService authentication;
    private final JsonCodec json;

    public NamespaceHttpResource(final LogicalNamespaceService namespaces, final VaultService vaults,
            final AuthorizationService authorization, final AuthService authentication, final JsonCodec json) {
        super(json);
        this.namespaces = namespaces;
        this.vaults = vaults;
        this.authorization = authorization;
        this.authentication = authentication;
        this.json = json;
    }

    @Override
    protected String basePath() {
        return "/api/v1/namespaces";
    }

    @Override
    protected List<Route> routes() {
        return List.of(Route.of("/", Set.of("POST")), Route.of("/{id}", Set.of("GET")),
                Route.of("/{id}/members", Set.of("POST")), Route.of("/{id}/vaults", Set.of("POST")),
                Route.of("/{namespaceId}/vaults/{vaultId}/members", Set.of("POST")));
    }

    @Override
    public boolean post(final HttpExchange exchange) {
        final var principal = principal(exchange);
        if (principal.isEmpty()) {
            exchange.json(401, Map.of("error", "unauthorized"));
            return true;
        }
        try {
            final var request = readRequest(exchange);
            if (isNamespaceMemberRoute(exchange)) {
                final var namespaceId = UUID.fromString(exchange.pathParam("id"));
                authorization.requireNamespace(principal.orElseThrow(), namespaceId, Role.OWNER);
                namespaces.grantNamespaceRole(principal.orElseThrow(), namespaceId,
                        UUID.fromString(text(request, "userId")), Role.valueOf(text(request, "role").toUpperCase()));
                exchange.noContent(204);
                return true;
            }
            if (isVaultMemberRoute(exchange)) {
                final var namespaceId = UUID.fromString(exchange.pathParam("namespaceId"));
                final var vaultId = UUID.fromString(exchange.pathParam("vaultId"));
                authorization.requireNamespace(principal.orElseThrow(), namespaceId, Role.OWNER);
                namespaces.grantVaultRole(principal.orElseThrow(), namespaceId, vaultId,
                        UUID.fromString(text(request, "userId")), Role.valueOf(text(request, "role").toUpperCase()));
                exchange.noContent(204);
                return true;
            }
            if (isVaultRoute(exchange)) {
                final var namespaceId = UUID.fromString(exchange.pathParam("id"));
                authorization.requireNamespace(principal.orElseThrow(), namespaceId, Role.OWNER);
                final var vault = vaults.create(new dev.rafex.ether.vault.application.port.in.CreateVaultUseCase.CreateVaultCommand(
                        text(request, "name"), text(request, "recipient"), nullableText(request, "description")));
                namespaces.attachVault(principal.orElseThrow(), namespaceId, vault.id());
                exchange.json(201, VaultView.from(vault));
                return true;
            }
            exchange.json(201, NamespaceView.from(namespaces.create(principal.orElseThrow(), text(request, "name"),
                    nullableText(request, "description"))));
        } catch (final AuthorizationService.AccessDeniedException e) {
            exchange.json(403, Map.of("error", "forbidden"));
        } catch (final LogicalNamespaceService.NamespaceAlreadyExistsException e) {
            exchange.json(409, Map.of("error", "namespace_already_exists"));
        } catch (final LogicalNamespaceService.NamespaceNotFoundException e) {
            exchange.json(404, Map.of("error", "namespace_not_found"));
        } catch (final IllegalArgumentException e) {
            exchange.json(422, Map.of("error", "invalid_request", "message", e.getMessage()));
        } catch (final RuntimeException e) {
            exchange.json(502, Map.of("error", "namespace_operation_failed", "message", e.getMessage()));
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
            final var namespaceId = UUID.fromString(exchange.pathParam("id"));
            authorization.requireNamespace(principal.orElseThrow(), namespaceId, Role.READ);
            exchange.json(200, NamespaceView.from(namespaces.get(namespaceId)));
        } catch (final AuthorizationService.AccessDeniedException e) {
            exchange.json(403, Map.of("error", "forbidden"));
        } catch (final LogicalNamespaceService.NamespaceNotFoundException e) {
            exchange.json(404, Map.of("error", "namespace_not_found"));
        } catch (final IllegalArgumentException e) {
            exchange.json(422, Map.of("error", "invalid_request", "message", e.getMessage()));
        }
        return true;
    }

    private java.util.Optional<dev.rafex.ether.vault.domain.model.AuthPrincipal> principal(
            final HttpExchange exchange) {
        if (!(exchange instanceof JettyHttpExchange jetty)) {
            return java.util.Optional.empty();
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

    private static boolean isVaultRoute(final HttpExchange exchange) {
        return exchange.path() != null && exchange.path().endsWith("/vaults");
    }

    private static boolean isNamespaceMemberRoute(final HttpExchange exchange) {
        return exchange.path() != null && exchange.path().endsWith("/members")
                && exchange.path().contains("/namespaces/");
    }

    private static boolean isVaultMemberRoute(final HttpExchange exchange) {
        return exchange.path() != null && exchange.path().contains("/vaults/")
                && exchange.path().endsWith("/members");
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

    private record NamespaceView(String id, String name, String description, String createdAt) {
        static NamespaceView from(final LogicalNamespace value) {
            return new NamespaceView(value.id().toString(), value.name(), value.description(),
                    value.createdAt().toString());
        }
    }

    private record VaultView(String id, String name, String recipient, String description, String status,
            String createdAt) {
        static VaultView from(final Vault value) {
            return new VaultView(value.id().toString(), value.name(), value.recipient(), value.description(),
                    value.status().name(), value.createdAt().toString());
        }
    }
}
