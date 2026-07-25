# Valoria API - Agent Handoff

Last reviewed: 2026-07-25

Read this document before changing authentication, OpenMU queries, VIP,
backups, migrations, or production behavior.

## 1. Purpose and current state

`ValoriaApi` is a Kotlin and Spring Boot API for the Valoria website. It:

- Authenticates OpenMU accounts using their existing BCrypt password hash.
- Issues guest, user, and administrator JWTs.
- Reads rankings, characters, guilds, server rates, and VIP status from OpenMU.
- Creates website accounts and grants the configured initial VIP trial.
- Stores notices, downloads, and audit records in the isolated `website`
  schema.
- Provides administrator endpoints for notices, backups, and VIP access.

Important commits:

- `351e6ba`: administrative VIP access API.
- `3a45fce`: Valoria client `0.1.2` download metadata.

Production builds the repository's `main` branch through `ValoriaDocker`.

## 2. Technology

- Kotlin
- Spring Boot
- Java 21
- Gradle wrapper
- Spring Security OAuth2 Resource Server
- HS256 JWT
- JDBC through `NamedParameterJdbcTemplate`
- Flyway for the `website` schema
- PostgreSQL

## 3. Schema ownership

The API connects to the same PostgreSQL database as OpenMU.

| Schema | Owner | API access |
| --- | --- | --- |
| `data` | OpenMU Entity Framework | Read; narrowly-scoped writes for account creation and VIP |
| `config` | OpenMU Entity Framework | Read-only |
| `website` | Valoria API Flyway | Read/write |

Safety rules:

1. Flyway migrations in this repository may alter only `website`.
2. Never create a Flyway migration for `data` or `config`.
3. OpenMU identifiers are quoted, case-sensitive PostgreSQL identifiers. Use
   schema-qualified names such as `data."Account"`.
4. Never infer table relationships or UUID meaning. Inspect the OpenMU model and
   current migration snapshot first.
5. Never bulk-update OpenMU accounts, characters, items, or configurations
   without a fresh verified backup.
6. Keep SQL parameters bound through `NamedParameterJdbcTemplate`; never
   concatenate untrusted values into SQL.

## 4. Migrations

Flyway migrations currently present:

- `V1__website_schema.sql`
- `V2__initial_notice_and_client_download.sql`
- `V3__update_client_download_0_1_1.sql`
- `V4__update_client_download_0_1_2.sql`

Applied migrations must be immutable. Add a new numbered migration instead of
editing an existing file.

The OpenMU table `data."AccountVipEntitlement"` is created by the OpenMU
migration `20260723004740_AddAccountVipEntitlement`, not by Flyway.

## 5. Authentication and authorization

All `/api/**` routes require a JWT except:

```text
POST /api/auth/guest
```

The guest endpoint returns a short-lived JWT with role `GUEST`. Public API
requests still require this guest token.

Roles:

- `GUEST`: public data and account login/registration bootstrap.
- `USER`: account data.
- `ADMIN`: website administration.

The administrator role is derived from OpenMU `data."Account"."State"` values
`2` or `3`. The browser cannot request or assign this role.

Security invariants:

- Never move the JWT signing secret into source code.
- `JWT_SECRET_BASE64` must decode to at least 32 random bytes.
- Never log JWTs, passwords, password hashes, database passwords, or request
  bodies containing credentials.
- Do not hash a password in the browser as a replacement for HTTPS. Such a hash
  becomes a reusable password equivalent.
- Keep rate limiting in the frontend Nginx configuration.
- Keep method-level `@PreAuthorize` protection on administration endpoints.

## 6. HTTP and HTTPS

`SecureTransportFilter` protects login and registration when
`REQUIRE_HTTPS=true`.

The production deployment temporarily uses HTTP because a domain and
certificate are not yet available. Therefore production currently uses
`REQUIRE_HTTPS=false`. This is a temporary test-only exception.

When a domain is available:

1. Terminate TLS at a reverse proxy.
2. Forward the correct `X-Forwarded-Proto`.
3. Set `FRONTEND_ORIGIN` to the exact HTTPS origin.
4. Set `REQUIRE_HTTPS=true`.
5. Retest CORS, login, registration, uploads, and downloads.

Do not add permissive wildcard CORS.

## 7. Token behavior

The frontend stores tokens in `sessionStorage`. A token can be valid by
expiration time but rejected after a signing-key change. `ValoriaWeb` commit
`4d7a65c` handles this by:

1. Clearing a rejected token after an HTTP `401`.
2. Obtaining a new guest token.
3. Retrying the original request once.

The API should continue returning `401` for invalid or rejected credentials and
`403` for an authenticated identity without the required role. Do not weaken
the API to accommodate stale browser tokens.

## 8. Account registration

Registration writes an OpenMU account using the established OpenMU identifiers
and BCrypt-compatible password format.

Trial behavior is controlled only by server environment:

- `TRIAL_VIP_DAYS`
- `TRIAL_VIP_LEVEL`

The browser must never send authoritative trial days or VIP level.

The account-creation trial source reference includes the new account ID. This
keeps it unique and prevents one trial record from colliding with another
account.

## 9. VIP administration

Controller:

```text
src/main/kotlin/com/valoria/api/controller/AdminVipController.kt
```

Repository:

```text
src/main/kotlin/com/valoria/api/repository/VipAdminRepository.kt
```

Endpoints, all requiring `ADMIN`:

- `GET /api/admin/vip/accounts?query=&limit=`
- `GET /api/admin/vip/accounts/{accountId}`
- `POST /api/admin/vip/accounts/{accountId}/entitlements`
- `POST /api/admin/vip/entitlements/{entitlementId}/revoke`

Grant rules:

- `vipLevel` is required and validated.
- `days` between the accepted DTO limits creates timed access.
- `days = null` creates permanent access.
- A timed grant extends the latest active timed expiration instead of discarding
  remaining time.
- `sourceReference` is optional, globally unique, and idempotent.
- Repeating the same source reference for the same account does not grant time
  twice.
- Using a source reference already assigned to another account is rejected.
- Grant and revoke actions are audited in `website`.

Legacy VIP:

- The old `IsVip` stat attribute is still read.
- Attribute definition ID:
  `195474D6-59A2-4033-9C30-8628ECC0097E`
- Legacy VIP is treated as permanent.
- Revoking entitlement rows does not remove legacy `IsVip`.
- Do not silently delete the legacy attribute during entitlement operations.

## 10. Public and account endpoints

Main endpoint groups:

- `POST /api/auth/guest`
- `POST /api/auth/login`
- `POST /api/auth/register`
- `GET /api/public/rankings/level`
- `GET /api/public/rankings/pk`
- `GET /api/public/rankings/guilds`
- `GET /api/public/server-rates`
- `GET /api/public/notices`
- `GET /api/public/mini-games`
- `GET /api/public/downloads`
- `GET /api/account`
- `GET /api/account/characters`

Game Masters must remain excluded from public rankings. Server XP is the
effective multiplication of the Game Server rate and Game Configuration rate.

## 11. Backup administration

Administrator endpoints:

- List the latest backups.
- Download a backup.
- Upload and validate a `.dump.gz`.
- Request restore with exact confirmation text.

Backup restoration is intentionally disabled by default:

```text
BACKUP_RESTORE_ENABLED=false
```

Do not enable restoration until the complete process has been tested in
staging. A safe restore requires stopping OpenMU and the API, creating a fresh
backup, validating the target dump, and verifying the restored database before
resuming traffic.

File-name validation, extension validation, size limits, archive validation,
path traversal protection, `ADMIN` authorization, exact confirmation text, and
audit logging must not be removed.

## 12. Environment variables

The repository contains only `.env.example`. Real values live outside Git.

Required variable names:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET_BASE64`
- `FRONTEND_ORIGIN`
- `REQUIRE_HTTPS`
- `TRIAL_VIP_DAYS`
- `TRIAL_VIP_LEVEL`
- `BACKUP_DIRECTORY`
- `BACKUP_RESTORE_ENABLED`
- `PG_RESTORE_PATH`

Never read or print secret values merely to confirm configuration. Check only
whether a required variable exists when diagnosing startup.

## 13. Development and validation

Windows:

```powershell
.\gradlew.bat test
```

Linux:

```bash
./gradlew test
```

Docker:

```bash
docker build -t valoria-api:local .
```

Before committing:

1. Run all tests.
2. Confirm Flyway migration validation passes.
3. Inspect `git diff --check`.
4. Confirm no secret, dump, key, token, or generated environment file is
   staged.
5. Test unauthorized, guest, user, and administrator access separately.

## 14. Deployment

Production is managed from `ValoriaDocker`:

```bash
cd /opt/valoria/openmu-docker
docker compose build --pull valoria-api
docker compose up -d --no-deps --force-recreate valoria-api
docker compose ps valoria-api
docker logs --tail 100 valoria-api
```

Required validation:

- Health check becomes `healthy`.
- Flyway validates and migrates only `website`.
- Guest token returns `200`.
- A public endpoint with that token returns `200`.
- An admin endpoint without a token returns `401`.
- An admin endpoint with a non-admin token returns `403`.

## 15. Resume checklist

1. Read `ValoriaDocker/AGENT_HANDOFF.md`.
2. Inspect local `git status`; preserve unrelated local changes.
3. Confirm the OpenMU fork migration and table expected by the requested API
   change already exist.
4. Determine whether the change belongs to `website`, `data`, or `config`.
5. Back up production before any OpenMU-schema write or migration.
6. Implement parameterized SQL and backend validation.
7. Add or update tests.
8. Commit and push before rebuilding production.
9. Rebuild only `valoria-api`.
10. Validate health, JWT roles, audit records, and logs.

