package dev.rafex.ether.vault.adapters.in.http;

import java.util.Set;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.jetty12.handler.ResourceHandler;
import dev.rafex.ether.json.JsonCodec;

public final class OpenApiResource extends ResourceHandler {

    private final JsonCodec json;

    public OpenApiResource(final JsonCodec json) {
        super(json);
        this.json = json;
    }

    @Override
    protected String basePath() {
        return "/openapi.json";
    }

    @Override
    public boolean get(final HttpExchange exchange) {
        exchange.json(200, json.readTree("""
                {
                  "openapi": "3.1.0",
                  "info": {"title": "Ether Vault API", "version": "0.3.0", "description": "Namespaces, role-based access and gopass-backed secrets."},
                  "servers": [{"url": "https://ether.rafex.io"}],
                  "paths": {
                    "/api/v1/auth/token": {
                      "post": {
                        "operationId": "createAccessToken",
                        "requestBody": {"required": true, "content": {"application/json": {"schema": {"$ref": "#/components/schemas/LoginRequest"}}}},
                        "responses": {"200": {"description": "Access token"}, "401": {"description": "Invalid credentials"}}
                      }
                    },
                    "/api/v1/auth/users": {
                      "post": {
                        "operationId": "createUser",
                        "security": [{"bearerAuth": []}],
                        "requestBody": {"required": true, "content": {"application/json": {"schema": {"$ref": "#/components/schemas/CreateUserRequest"}}}},
                        "responses": {"201": {"description": "User created"}, "403": {"description": "auth:admin scope required"}}
                      }
                    },
                    "/api/v1/namespaces": {
                      "post": {
                        "operationId": "createNamespace",
                        "security": [{"bearerAuth": []}],
                        "requestBody": {"required": true, "content": {"application/json": {"schema": {"$ref": "#/components/schemas/NamespaceRequest"}}}},
                        "responses": {"201": {"description": "Namespace created"}, "409": {"description": "Already exists"}}
                      }
                    },
                    "/api/v1/namespaces/{namespaceId}": {
                      "get": {
                        "operationId": "getNamespace",
                        "security": [{"bearerAuth": []}],
                        "parameters": [{"name": "namespaceId", "in": "path", "required": true, "schema": {"type": "string", "format": "uuid"}}],
                        "responses": {"200": {"description": "Namespace"}, "403": {"description": "Forbidden"}}
                      }
                    },
                    "/api/v1/namespaces/{namespaceId}/vaults": {
                      "post": {
                        "operationId": "createVault",
                        "security": [{"bearerAuth": []}],
                        "parameters": [{"name": "namespaceId", "in": "path", "required": true, "schema": {"type": "string", "format": "uuid"}}],
                        "requestBody": {"required": true, "content": {"application/json": {"schema": {"$ref": "#/components/schemas/CreateVaultRequest"}}}},
                        "responses": {"201": {"description": "Vault created"}, "403": {"description": "Owner role required"}}
                      }
                    },
                    "/api/v1/namespaces/{namespaceId}/members": {
                      "post": {
                        "operationId": "grantNamespaceRole",
                        "security": [{"bearerAuth": []}],
                        "parameters": [{"name": "namespaceId", "in": "path", "required": true, "schema": {"type": "string", "format": "uuid"}}],
                        "requestBody": {"required": true, "content": {"application/json": {"schema": {"$ref": "#/components/schemas/MemberRoleRequest"}}}},
                        "responses": {"204": {"description": "Role granted"}, "403": {"description": "Owner role required"}}
                      }
                    },
                    "/api/v1/namespaces/{namespaceId}/vaults/{vaultId}/members": {
                      "post": {
                        "operationId": "grantVaultRole",
                        "security": [{"bearerAuth": []}],
                        "parameters": [
                          {"name": "namespaceId", "in": "path", "required": true, "schema": {"type": "string", "format": "uuid"}},
                          {"name": "vaultId", "in": "path", "required": true, "schema": {"type": "string", "format": "uuid"}}
                        ],
                        "requestBody": {"required": true, "content": {"application/json": {"schema": {"$ref": "#/components/schemas/MemberRoleRequest"}}}},
                        "responses": {"204": {"description": "Role granted"}, "403": {"description": "Owner role required"}}
                      }
                    },
                    "/api/v1/vaults/{namespaceId}/namespaces/{vaultId}/secrets": {
                      "post": {
                        "operationId": "insertSecret",
                        "security": [{"bearerAuth": []}],
                        "parameters": [
                          {"name": "namespaceId", "in": "path", "required": true, "schema": {"type": "string", "format": "uuid"}},
                          {"name": "vaultId", "in": "path", "required": true, "schema": {"type": "string", "format": "uuid"}}
                        ],
                        "requestBody": {"required": true, "content": {"application/json": {"schema": {"$ref": "#/components/schemas/InsertSecretRequest"}}}},
                        "responses": {"201": {"description": "Secret reference"}, "403": {"description": "Write role required"}}
                      },
                      "get": {
                        "operationId": "readSecretBySelector",
                        "security": [{"bearerAuth": []}],
                        "parameters": [
                          {"name": "namespaceId", "in": "path", "required": true, "schema": {"type": "string", "format": "uuid"}},
                          {"name": "vaultId", "in": "path", "required": true, "schema": {"type": "string", "format": "uuid"}},
                          {"name": "secretId", "in": "query", "schema": {"type": "string", "format": "uuid"}},
                          {"name": "shortName", "in": "query", "schema": {"type": "string"}},
                          {"name": "friendlyName", "in": "query", "schema": {"type": "string"}},
                          {"name": "path", "in": "query", "schema": {"type": "string"}}
                        ],
                        "responses": {"200": {"description": "Secret value"}, "403": {"description": "Read role required"}, "409": {"description": "Ambiguous selector"}}
                      }
                    },
                    "/api/v1/vaults/{namespaceId}/namespaces/{vaultId}/secrets/{secretId}": {
                      "get": {
                        "operationId": "readSecretById",
                        "security": [{"bearerAuth": []}],
                        "parameters": [
                          {"name": "namespaceId", "in": "path", "required": true, "schema": {"type": "string", "format": "uuid"}},
                          {"name": "vaultId", "in": "path", "required": true, "schema": {"type": "string", "format": "uuid"}},
                          {"name": "secretId", "in": "path", "required": true, "schema": {"type": "string", "format": "uuid"}}
                        ],
                        "responses": {"200": {"description": "Secret value"}, "403": {"description": "Read role required"}}
                      }
                    }
                  },
                  "components": {
                    "securitySchemes": {"bearerAuth": {"type": "http", "scheme": "bearer", "bearerFormat": "opaque token"}},
                    "schemas": {
                      "LoginRequest": {"type": "object", "required": ["username", "password"], "properties": {"username": {"type": "string"}, "password": {"type": "string", "format": "password"}}},
                      "CreateUserRequest": {"type": "object", "required": ["username", "password"], "properties": {"username": {"type": "string"}, "password": {"type": "string", "format": "password"}, "scopes": {"type": "array", "items": {"type": "string"}}}},
                      "NamespaceRequest": {"type": "object", "required": ["name"], "properties": {"name": {"type": "string"}, "description": {"type": "string"}}},
                      "CreateVaultRequest": {"type": "object", "required": ["name", "recipient"], "properties": {"name": {"type": "string"}, "recipient": {"type": "string"}, "description": {"type": "string"}}},
                      "MemberRoleRequest": {"type": "object", "required": ["userId", "role"], "properties": {"userId": {"type": "string", "format": "uuid"}, "role": {"type": "string", "enum": ["READ", "WRITE", "OWNER"]}}},
                      "InsertSecretRequest": {"type": "object", "required": ["friendlyName", "value"], "properties": {"namespace": {"type": "string"}, "shortName": {"type": "string"}, "friendlyName": {"type": "string"}, "path": {"type": "string"}, "value": {"type": "string"}}}
                    }
                  }
                }
                """));
        return true;
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET");
    }
}
