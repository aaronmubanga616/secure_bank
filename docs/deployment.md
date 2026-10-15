# Running and operating SecureBank

## Local container setup

Install Docker Compose and Node 24. From the repository root:

```sh
npm ci
node scripts/setup-env.mjs
docker compose --profile monitoring up -d --build
node scripts/wait-for-stack.mjs
```

The setup script generates random credentials in ignored `.env` and a private metrics token in `.local`. It preserves existing values. Do not commit either directory or print resolved Compose configuration in public logs.

| Service                 | Local address                         |
| ----------------------- | ------------------------------------- |
| Customer banking        | http://localhost:8088                 |
| Staff dashboard         | http://localhost:8089                 |
| API / health            | http://localhost:8180/actuator/health |
| Development email inbox | http://localhost:8025                 |
| Prometheus              | http://localhost:9090                 |
| Grafana                 | http://localhost:3000                 |

All published ports bind to loopback. Omitting `--profile monitoring` starts the banking apps without the dashboards. PostgreSQL, Redis and Kafka keep named volumes. `docker compose --profile monitoring down` stops services without removing data; avoid `down -v` unless intentionally destroying your disposable simulator data.

## First staff account

In your private `.env`, set `BOOTSTRAP_ADMIN_ENABLED=true`, choose `BOOTSTRAP_ADMIN_EMAIL`, and retain the generated strong `BOOTSTRAP_ADMIN_PASSWORD`. Start or recreate the backend. This provisions the first administrator only when no administrator exists. Sign in at the staff dashboard, enroll an authenticator, save the recovery codes privately, and sign in again using MFA. Staff operations require an MFA-authenticated session.

Set `BOOTSTRAP_ADMIN_ENABLED=false` afterwards and recreate the backend. Provision additional staff from the administrator interface with a reason. Public registration always creates a customer. No default password is in the source. Grafana uses `admin` with the separately generated `GRAFANA_ADMIN_PASSWORD`.

## Customer demo

Register a fictional customer, open its verification message in Mailpit and follow the link. Create a current or savings account. Deposit simulated funds, save another customer's account as a recipient, and review an internal transfer. Statements download from posted journal history; held transfers do not affect balances until approved. A high-risk transfer appears in the staff investigation queue.

Mailpit is an intentionally local development inbox. It must not be exposed publicly. The seeded database contains policies and the simulated cash ledger, rather than pre-funded customer accounts.

## Host development

Start `docker compose up -d postgres redis kafka mailpit`. Export your private `.env` values into the backend process, then run `mvn -B -s .mvn/settings.xml spring-boot:run` inside `backend`. It listens on 8180 by default. Run `npm run dev:web` and `npm run dev:admin` in separate terminals; Vite proxies `/api` to 8180. See [mobile setup](mobile.md) for phone networking and native builds. On Windows, use an ASCII-only checkout path for Java or build the backend in Docker.

## Deployment boundary

This repository is a banking simulator, not a licensed banking service. Containers provide a reproducible local environment, not a hardened public banking deployment. Before hosting a portfolio demo, use HTTPS, separate private service networks, managed secrets, authenticated encrypted Kafka/Redis/database connections, a real transactional mail provider, restricted staff access, backups, resource limits, image scanning, and an external security review. Disable OpenAPI with `API_DOCS_ENABLED=false`; set explicit browser CORS origins and the real customer URL. Mobile release builds need an HTTPS API URL and your own signing/account configuration.

Do not rotate `ENCRYPTION_KEY` by simply replacing it: existing MFA secrets and queued email need a versioned decrypt/re-encrypt migration. Changing JWT signing material invalidates existing access tokens. Rotate database/Redis/metrics credentials together with their clients. Keep encryption keys with encrypted backups in a separate secret store; test restore into an isolated environment before relying on a backup.

Flyway owns schema changes; never edit an applied migration or disable journal constraints to repair a balance. Back up PostgreSQL consistently and retain Kafka/outbox state for investigation. Redis loss removes short-lived challenges and counters; durable money remains in PostgreSQL. Financial receipt state and the immutable journal are authoritative during outages. See [monitoring](monitoring.md) for event lag, parked mail and recovery.
