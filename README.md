# SecureBank

[![SecureBank checks](https://github.com/aaronmubanga616/secure_bank/actions/workflows/ci.yml/badge.svg)](https://github.com/aaronmubanga616/secure_bank/actions/workflows/ci.yml)

A simulated digital banking platform for Zambia, built around an auditable double-entry ledger. Customer web, Expo mobile, and a separate administration app share one Spring Boot API.

**Educational software. All funds are simulated ZMW. Do not enter real banking or payment-card information.**

## What it does

- Customer banking on the web, Android and iOS: verified registration, authenticator MFA, current/savings accounts, exact balances, transfers, simulated deposits/withdrawals, recipients, recurring payments, notifications and PDF statements.
- A separate staff app: customer support, account controls, transaction search, fraud investigations, immutable audit history and role-restricted policy changes. Staff access requires MFA.
- A PostgreSQL double-entry journal: balanced immutable postings, account locking, daily limits, request idempotency and safe concurrent retries.
- Durable background work: transactional outbox, Kafka consumers with duplicate protection, dead letters and encrypted security email jobs.
- Private operational metrics, a provisioned Grafana dashboard, alert rules, Docker Compose and automated checks.

React 19 · TypeScript · Expo 57 · React Native · Spring Boot 3.5 · Java 21 · PostgreSQL 17 · Redis · Kafka

## Project layout

| Directory            | Purpose                                             |
| -------------------- | --------------------------------------------------- |
| `apps/web`           | Customer banking, React and TypeScript              |
| `apps/admin`         | Staff dashboard, React and TypeScript               |
| `apps/mobile`        | Android and iOS, Expo Router                        |
| `packages/contracts` | Shared API types and validation                     |
| `packages/ui`        | Shared browser components and session handling      |
| `backend`            | Spring Boot modular monolith                        |
| `infrastructure`     | Gateway and monitoring configuration                |
| `docs`               | Architecture, development, security, and API guides |

## Run locally

The container setup requires Node 24 and Docker Compose. The mobile app runs separately using Expo.

```sh
npm ci
node scripts/setup-env.mjs
docker compose --profile monitoring up -d --build
node scripts/wait-for-stack.mjs
```

Open [customer banking](http://localhost:8088), [staff banking](http://localhost:8089), [the local email inbox](http://localhost:8025) or [Grafana](http://localhost:3000). Register a fictional customer and verify its email through the local inbox. The [runbook](docs/deployment.md) explains first administrator setup, ports, credentials, backups and deployment boundaries.

For hot-reloading development, start the infrastructure and backend as described in the runbook, then use separate terminals:

```sh
npm run dev:web       # http://localhost:5173
npm run dev:admin     # http://localhost:5174
npm run dev:mobile    # Expo development server
```

The API uses `http://localhost:8180` when run locally or exposed by Compose.

`scripts/setup-env.mjs` generates random local secrets and preserves an existing `.env`. Never commit this file. Set the mobile app's `EXPO_PUBLIC_API_URL` to your computer's LAN address; `localhost` on a physical phone refers to the phone.

### Checks

```sh
npm run typecheck
npm run lint
npm test
npm run security:check
npm run build
npm run build:mobile
cd backend
mvn -B -ntp -s .mvn/settings.xml clean verify
```

The backend suite passes 56 unit/HTTP/integration checks against actual PostgreSQL, Redis and Kafka containers. Ten JavaScript tests cover shared money/session logic. Live browser journeys cover customer and staff banking. See [testing](docs/testing.md) and [recorded results](docs/verification.md) for evidence and limits.

Android/iOS JavaScript exports validate bundling. Native device builds still require Android tooling or macOS/Xcode, or an Expo EAS build. Physical biometrics and native packaging have not been verified. Three known upstream Expo dependency advisories remain documented in the [security guide](docs/security/README.md); a passing reviewed-baseline check does not mean the dependency tree has no advisories.

The implementation follows the phases in [the development plan](docs/development.md). Each completed stage is committed separately, with validation recorded alongside the change.

See [architecture](docs/architecture/README.md) for the system boundaries and financial invariants.

## Guides

[Database model](docs/architecture/database.md) · [Ledger](docs/architecture/ledger.md) · [Events and fraud](docs/architecture/events-and-risk.md) · [API](docs/api/README.md) · [Mobile](docs/mobile.md) · [Staff roles](docs/staff.md) · [Monitoring](docs/monitoring.md) · [Security](docs/security/README.md)
