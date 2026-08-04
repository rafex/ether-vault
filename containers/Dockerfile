FROM golang:1.24-bookworm AS gopass-build

ARG GOPASS_VERSION=v1.16.1
RUN GOBIN=/out go install github.com/gopasspw/gopass@${GOPASS_VERSION}

FROM eclipse-temurin:25-jre-jammy

RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates curl gnupg git \
    && rm -rf /var/lib/apt/lists/*

COPY --from=gopass-build /out/gopass /usr/local/bin/gopass

RUN useradd --system --create-home --home-dir /home/ether-vault --shell /usr/sbin/nologin ether-vault \
    && mkdir -p /var/lib/ether-vault \
    && chown -R ether-vault:ether-vault /var/lib/ether-vault /home/ether-vault

WORKDIR /app
COPY ether-vault/target/ether-vault.jar /app/ether-vault.jar

ENV PORT=8080 \
    DATABASE_PATH=/var/lib/ether-vault/ether-vault.sqlite \
    VAULT_ROOT=/var/lib/ether-vault/vaults \
    GOPASS_BIN=gopass

VOLUME ["/var/lib/ether-vault"]
EXPOSE 8080
USER ether-vault

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/ether-vault.jar"]
