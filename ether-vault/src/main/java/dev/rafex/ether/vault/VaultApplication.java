package dev.rafex.ether.vault;

import java.util.List;

import dev.rafex.ether.http.jetty12.JettyModule;
import dev.rafex.ether.http.jetty12.JettyServerConfig;
import dev.rafex.ether.http.jetty12.JettyServerFactory;
import dev.rafex.ether.http.jetty12.routing.JettyRouteRegistry;
import dev.rafex.ether.json.JsonUtils;
import dev.rafex.ether.vault.adapters.in.http.OpenApiResource;
import dev.rafex.ether.vault.adapters.in.http.AuthHttpResource;
import dev.rafex.ether.vault.adapters.in.http.NamespaceHttpResource;
import dev.rafex.ether.vault.adapters.in.http.VaultHttpResource;
import dev.rafex.ether.vault.adapters.out.gopass.ProcessGopassAdapter;
import dev.rafex.ether.vault.adapters.out.persistence.SqliteAuthRepository;
import dev.rafex.ether.vault.adapters.out.persistence.SqliteAccessRepository;
import dev.rafex.ether.vault.adapters.out.persistence.SqliteSecretCatalog;
import dev.rafex.ether.vault.adapters.out.persistence.SqliteVaultRepository;
import dev.rafex.ether.vault.application.service.AuthService;
import dev.rafex.ether.vault.application.service.AuthorizationService;
import dev.rafex.ether.vault.application.service.LogicalNamespaceService;
import dev.rafex.ether.vault.application.service.SecretService;
import dev.rafex.ether.vault.application.service.VaultService;
import dev.rafex.ether.vault.config.VaultConfig;

public final class VaultApplication {

    private VaultApplication() {
    }

    public static void main(final String[] args) throws Exception {
        final var config = VaultConfig.fromEnv();
        final var repository = new SqliteVaultRepository(config.databasePath());
        final var catalog = new SqliteSecretCatalog(config.databasePath());
        final var authRepository = new SqliteAuthRepository(config.databasePath());
        final var accessRepository = new SqliteAccessRepository(config.databasePath());
        final var auth = new AuthService(authRepository, config.tokenTtl());
        auth.ensureBootstrapUser(config.bootstrapUsername(), config.bootstrapPassword(), config.bootstrapScopes());
        final var provisioner = new ProcessGopassAdapter(config.gopassBinary(), config.gpgHome());
        final var service = new VaultService(repository, provisioner, config.vaultRoot().toAbsolutePath().toString());
        final var logicalNamespaces = new LogicalNamespaceService(accessRepository);
        final var authorization = new AuthorizationService(accessRepository);
        final var secrets = new SecretService(repository, provisioner, catalog);
        final var json = JsonUtils.codec();

        final JettyModule module = new JettyModule() {
            @Override
            public void registerRoutes(final JettyRouteRegistry routes,
                    final dev.rafex.ether.http.jetty12.JettyModuleContext context) {
                final var vaults = new VaultHttpResource(secrets, secrets, json, auth, authorization);
                routes.add("/api/v1/vaults", vaults);
                routes.add("/api/v1/vaults/*", vaults);
                final var namespaceResource = new NamespaceHttpResource(logicalNamespaces, service, authorization,
                        auth, json);
                routes.add("/api/v1/namespaces", namespaceResource);
                routes.add("/api/v1/namespaces/*", namespaceResource);
                final var authentication = new AuthHttpResource(auth, json);
                routes.add("/api/v1/auth", authentication);
                routes.add("/api/v1/auth/*", authentication);
                routes.add("/openapi.json", new OpenApiResource(json));
            }
        };

        final var runner = JettyServerFactory.create(JettyServerConfig.fromEnv(), json, null, List.of(module));
        runner.start();
        runner.await();
    }
}
