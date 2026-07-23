# Valoria API

API Kotlin/Spring Boot para o site do servidor Valoria. Ela lê rankings e contas diretamente do PostgreSQL do OpenMU e mantém conteúdo editorial no schema isolado `website`.

## Segurança

- Todas as rotas `/api/**`, exceto o bootstrap `/api/auth/guest`, exigem JWT.
- Em produção, `REQUIRE_HTTPS=true` rejeita login e cadastro que não chegarem por TLS.
- O token anônimo de curta duração permite proteger também rankings, avisos, taxas e downloads.
- Login e cadastro nunca aceitam papel administrativo ou dias VIP enviados pelo navegador.
- Contas `GameMaster` e personagens com status GM não aparecem nos rankings.
- Segredos são lidos exclusivamente por variáveis de ambiente. Arquivos `.env`, chaves e certificados estão ignorados pelo Git.
- Restauração é desabilitada por padrão, exige papel `ADMIN`, frase `RESTORE <arquivo>` e gera auditoria.

A senha aparece no payload local do navegador porque o servidor precisa verificá-la contra BCrypt. Não aplique hash simples no frontend: esse hash se tornaria uma credencial reutilizável. HTTPS cifra integralmente headers e corpo durante o transporte. Para produção, publique o Nginx atrás de Caddy, Traefik ou outro proxy TLS e habilite `REQUIRE_HTTPS=true`.

## Integração OpenMU

O papel administrativo é derivado de `data.Account.State` (`2` ou `3`). Senhas usam BCrypt compatível com o OpenMU. O cadastro concede o trial configurado por `TRIAL_VIP_DAYS` e `TRIAL_VIP_LEVEL` em `data.AccountVipEntitlement`.

XP exibida:

`multiplicador = GameServerDefinition.ExperienceRate × GameConfiguration.ExperienceRate`

## Desenvolvimento

Requisitos: JDK 21 e Gradle 9, ou apenas Docker.

```bash
docker build -t valoria-api:local .
```

Crie um arquivo de ambiente fora do repositório tomando `.env.example` apenas como referência. Gere o segredo JWT com pelo menos 32 bytes aleatórios:

```bash
openssl rand -base64 48
```

## Docker com OpenMU

O overlay em `deploy/docker-compose.openmu-overlay.yml` usa a rede externa `openmu-network` e espera os repositórios `ValoriaApi` e `ValoriaWeb` lado a lado. Exemplo:

```bash
VALORIA_ENV_FILE=/opt/valoria/secrets/api.env \
OPENMU_BACKUP_DIR=/opt/openmu/backups \
VALORIA_HTTP_PORT=8081 \
docker compose -f deploy/docker-compose.openmu-overlay.yml up -d --build
```

Para produção, monte os backups inicialmente como somente leitura e mantenha `BACKUP_RESTORE_ENABLED=false`. Habilite escrita e restauração apenas depois de validar manutenção, permissões e uma restauração completa em homologação.

## Endpoints

- `POST /api/auth/guest`, `/login`, `/register`
- `GET /api/public/rankings/level`, `/pk`, `/guilds`
- `GET /api/public/server-rates`, `/notices`, `/mini-games`, `/downloads`
- `GET /api/account`, `/api/account/characters`
- `POST /api/admin/notices`
- `GET /api/admin/backups`, download, upload e restauração

Consulte `docs/DEPLOYMENT.md` para topologia, checklist e comparação inicial entre Hostinger VPS e AWS Lightsail.
