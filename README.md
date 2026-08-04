# ether-vault

Microservicio Java 25, basado en Ether y organizado con arquitectura hexagonal, para provisionar vaults aislados respaldados por [gopass](https://www.gopass.pw/), organizados en namespaces colaborativos.

## Arquitectura

- `domain`: modelo y reglas del vault.
- `application`: casos de uso y puertos.
- `adapters/in/http`: API REST sobre Jetty 12/Ether.
- `adapters/out/persistence`: repositorio SQLite.
- `adapters/out/gopass`: adaptador que ejecuta el CLI de gopass con un `HOME` aislado por vault.

SQLite guarda únicamente metadatos, referencias, namespaces, roles, membresías y credenciales derivadas. Los valores se cifran y se almacenan en gopass; nunca se guardan en SQLite. La autorización usa `OWNER`, `WRITE` y `READ`: un vault hereda el rol del namespace salvo que tenga una asignación explícita.

## API

Primero se obtiene un token mediante las credenciales bootstrap configuradas en el entorno:

```bash
TOKEN=$(curl -sS -X POST http://localhost:8080/api/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"cambia-esta-password"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
```

El usuario bootstrap puede crear usuarios adicionales mediante `POST /api/v1/auth/users`; requiere el scope `auth:admin`:

```bash
curl -X POST http://localhost:8080/api/v1/auth/users \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"username":"bob","password":"another-secure-password","scopes":["vault:read"]}'
```

Crear un namespace asigna automáticamente el rol `OWNER` al usuario autenticado:

```bash
NAMESPACE_RESPONSE=$(curl -sS -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"team","description":"Shared team namespace"}')
NAMESPACE_ID=$(printf '%s' "$NAMESPACE_RESPONSE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
```

Crear un vault dentro del namespace también asigna `OWNER` al creador:

```bash
curl -X POST http://localhost:8080/api/v1/namespaces/$NAMESPACE_ID/vaults \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"personal","recipient":"alice@example.com","description":"Personal vault"}'
```

Compartir un namespace o limitar un vault usa el mismo catálogo de roles:

```bash
curl -X POST http://localhost:8080/api/v1/namespaces/$NAMESPACE_ID/members \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"userId":"USER_ID","role":"WRITE"}'

curl -X POST http://localhost:8080/api/v1/namespaces/$NAMESPACE_ID/vaults/$VAULT_ID/members \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"userId":"USER_ID","role":"READ"}'
```

`POST /api/v1/vaults/{namespaceId}/namespaces/{vaultId}/secrets` inserta un valor. El valor no se devuelve; la respuesta contiene la referencia `secretId` y sus nombres:

```bash
curl -X POST http://localhost:8080/api/v1/vaults/$NAMESPACE_ID/namespaces/$VAULT_ID/secrets \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"namespace":"personal","shortName":"email","friendlyName":"Personal email","value":"hunter2"}'

curl -G http://localhost:8080/api/v1/vaults/$NAMESPACE_ID/namespaces/$VAULT_ID/secrets \
  -H "Authorization: Bearer $TOKEN" \
  --data-urlencode 'secretId=SECRET_ID'

curl -G http://localhost:8080/api/v1/vaults/$NAMESPACE_ID/namespaces/$VAULT_ID/secrets \
  -H "Authorization: Bearer $TOKEN" \
  --data-urlencode 'shortName=email'

curl -G http://localhost:8080/api/v1/vaults/$NAMESPACE_ID/namespaces/$VAULT_ID/secrets \
  -H "Authorization: Bearer $TOKEN" \
  --data-urlencode 'friendlyName=Personal email'

curl -G http://localhost:8080/api/v1/vaults/$NAMESPACE_ID/namespaces/$VAULT_ID/secrets \
  -H "Authorization: Bearer $TOKEN" \
  --data-urlencode 'path=personal/email'
```

La lectura acepta exactamente uno de `secretId`, `shortName`, `friendlyName` o `path`. `shortName` y `friendlyName` deben resolver una única referencia dentro del vault. También existe la forma directa `/secrets/{secretId}`.

`GET /openapi.json` expone el contrato. `GET /health` es el endpoint de salud integrado de Ether.

El destinatario debe existir en el keyring GPG que usa el proceso. Para producción, monta/configura ese keyring mediante `GPG_HOME`; el contenedor no genera claves automáticamente.

## Configuración

| Variable | Default | Uso |
|---|---|---|
| `AUTH_BOOTSTRAP_USERNAME` | — | Usuario inicial de autenticación SQLite |
| `AUTH_BOOTSTRAP_PASSWORD` | — | Password inicial; mínimo 12 caracteres |
| `AUTH_BOOTSTRAP_SCOPES` | `auth:admin vault:read vault:write` | Scopes del usuario inicial |
| `AUTH_TOKEN_TTL_SECONDS` | `3600` | Vigencia del token bearer |
| `PORT` | `8080` | Puerto HTTP |
| `DATABASE_PATH` | `./data/ether-vault.sqlite` | Base SQLite |
| `VAULT_ROOT` | `./data/vaults` | Directorio de homes aislados |
| `GOPASS_BIN` | `gopass` | Binario de gopass |
| `GPG_HOME` | — | Keyring GPG opcional |

## Desarrollo

```bash
export AUTH_BOOTSTRAP_USERNAME=admin
export AUTH_BOOTSTRAP_PASSWORD='local-development-password'
make test
make package
```

Para construir el contenedor, primero ejecuta `make package` y después `docker compose -f containers/docker-compose.yml up --build`.

El `docker-compose.yml` incluye Caddy para publicar `https://ether.rafex.io` y obtener/renovar el certificado TLS automáticamente. Antes de levantarlo, apunta el DNS del dominio al servidor y prepara el keyring GPG que contendrá los destinatarios autorizados:

```bash
docker compose -f containers/docker-compose.yml run --rm --entrypoint gpg ether-vault \
  --homedir /var/lib/ether-vault/gnupg --import recipient-public-key.asc
```

En una instalación real conviene ejecutar esa importación contra el volumen persistente y no enviar claves privadas al contenedor si solo se usará para cifrar nuevos secretos.
