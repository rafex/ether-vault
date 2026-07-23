package dev.rafex.ether.vault.adapters.in.http;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.ether.http.jetty12.handler.ResourceHandler;
import dev.rafex.ether.json.JsonCodec;
import dev.rafex.ether.vault.application.service.AuthService;
import dev.rafex.ether.vault.domain.model.AuthPrincipal;
import dev.rafex.ether.vault.domain.model.AuthUser;

public final class AuthHttpResource extends ResourceHandler {

    private static final int MAX_BODY_BYTES = 16 * 1024;
    private final AuthService authentication;
    private final JsonCodec json;

    public AuthHttpResource(final AuthService authentication, final JsonCodec json) {
        super(json);
        this.authentication = authentication;
        this.json = json;
    }

    @Override
    protected String basePath() {
        return "/api/v1/auth";
    }

    @Override
    protected List<Route> routes() {
        return List.of(Route.of("/token", Set.of("POST")), Route.of("/users", Set.of("POST")));
    }

    @Override
    public boolean post(final HttpExchange exchange) {
        try {
            final var request = readRequest(exchange);
            if (exchange.path() != null && exchange.path().endsWith("/users")) {
                final var actor = principal(exchange).orElseThrow(AuthService.AuthorizationException::new);
                final var user = authentication.createUser(actor, text(request, "username"),
                        text(request, "password"), scopes(request));
                exchange.json(201, UserView.from(user));
                return true;
            }
            final var token = authentication.login(text(request, "username"), text(request, "password"));
            exchange.json(200, new TokenView(token.accessToken(), "Bearer", token.expiresAt().toString(),
                    token.scopes()));
        } catch (final AuthService.AuthenticationException e) {
            exchange.json(401, Map.of("error", "invalid_credentials"));
        } catch (final AuthService.AuthorizationException e) {
            exchange.json(403, Map.of("error", "forbidden"));
        } catch (final IllegalArgumentException e) {
            exchange.json(422, Map.of("error", "invalid_request", "message", e.getMessage()));
        } catch (final RuntimeException e) {
            exchange.json(409, Map.of("error", "user_already_exists_or_invalid", "message", e.getMessage()));
        }
        return true;
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

    private static String text(final JsonNode request, final String field) {
        final var node = request == null ? null : request.get(field);
        final var value = node == null || node.isNull() ? null : node.asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private java.util.Optional<AuthPrincipal> principal(final HttpExchange exchange) {
        if (!(exchange instanceof JettyHttpExchange jetty)) {
            return java.util.Optional.empty();
        }
        final var header = jetty.request().getHeaders().get("Authorization");
        final var token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        return authentication.authenticate(token);
    }

    private static Set<String> scopes(final JsonNode request) {
        final var node = request == null ? null : request.get("scopes");
        if (node == null || !node.isArray()) {
            return Set.of("vault:read");
        }
        final var values = new java.util.HashSet<String>();
        node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                values.add(value.asText().trim());
            }
        });
        return Set.copyOf(values);
    }

    private record TokenView(String accessToken, String tokenType, String expiresAt, Set<String> scopes) {
    }

    private record UserView(String id, String username, Set<String> scopes, String createdAt) {
        static UserView from(final AuthUser user) {
            return new UserView(user.id().toString(), user.username(), user.scopes(), user.createdAt().toString());
        }
    }
}
