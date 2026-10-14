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

The implementation follows the phases in [the development plan](docs/development.md). Each completed stage is committed separately, with validation recorded alongside the change.

See [architecture](docs/architecture/README.md) for the system boundaries and financial invariants.
