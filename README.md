# SecureBank

A simulated digital banking platform for Zambia, built around an auditable double-entry ledger. Customer web, Expo mobile, and a separate administration app share one Spring Boot API.

**Educational software. All funds are simulated ZMW. Do not enter real banking or payment-card information.**

## Project layout

| Directory | Purpose |
| --- | --- |
| `apps/web` | Customer banking, React and TypeScript |
| `apps/admin` | Staff dashboard, React and TypeScript |
| `apps/mobile` | Android and iOS, Expo Router |
| `packages/contracts` | Shared API types and validation |
| `backend` | Spring Boot modular monolith |
| `infrastructure` | Gateway and monitoring configuration |
| `docs` | Architecture, development, security, and API guides |

## Development

Requires Node 24, Java 21, Maven 3.9+, and Docker Compose. The mobile app runs on your computer using Expo; it does not run in a container.

```sh
npm ci
node scripts/setup-env.mjs
docker compose up -d postgres redis kafka
# Load the values from .env into your shell before running the backend locally.
cd backend
mvn spring-boot:run
```

In separate terminals from the repository root:

```sh
npm run dev:web       # http://localhost:5173
npm run dev:admin     # http://localhost:5174
npm run dev:mobile    # Expo development server
```

For the complete container stack, use `docker compose up --build`. The customer gateway is at `http://localhost:8088`; staff use `http://localhost:8089`.

`scripts/setup-env.mjs` generates random local secrets and preserves an existing `.env`. Never commit this file. Set the mobile app's `EXPO_PUBLIC_API_URL` to your computer's LAN address; `localhost` on a physical phone refers to the phone.

### Checks

```sh
npm run typecheck
npm run lint
npm run build
npm run build:mobile
cd backend && mvn verify
```

Android/iOS JavaScript exports validate bundling. Native device builds still require Android tooling or macOS/Xcode, or an Expo EAS build.

The implementation follows the phases in [the development plan](docs/development.md). Each completed stage is committed separately, with validation recorded alongside the change.

See [architecture](docs/architecture/README.md) for the system boundaries and financial invariants.
