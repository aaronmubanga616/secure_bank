# Verification record

## Foundation

Verified locally on 2026-10-14:

- Customer and staff Vite production builds passed.
- Type checks passed for web, admin, mobile, and shared contracts.
- ESLint passed without warnings.
- Expo exported Android and iOS Hermes bundles; dependency compatibility check passed.
- Maven verification passed: three HTTP security tests and one PostgreSQL migration integration test.
- Docker Compose configuration validated; PostgreSQL, Redis, Kafka, and Mailpit ran locally.
- Live API on port 8180 returned 200 for simulator metadata and health, and denied anonymous account access.

The machine's default Java launcher and the emoji parent directory required a Java 21 toolchain and an ASCII temporary build copy. Repository source, rather than generated build output or local secrets, is committed.

## Authentication

Full authentication verification passed eight unit/HTTP tests and five integration tests, without failures or skips. PostgreSQL and Redis were actual isolated containers; outbound email was mocked. Review added a login row lock to serialize session creation with password/security changes; the complete suite passed again after that change.

Known dependency advisories are documented in [security](security/README.md). Native JavaScript exports do not verify on-device biometrics, app-store packaging, or physical device behavior.

## Profiles and accounts

Backend verification passed eight unit/HTTP tests and eight integration tests. Added checks prove owner-scoped account reads, duplicate account-type prevention, and safe profile serialization. Shared client tests passed four checks for exact currency display, malformed values, concurrent refresh, and logout during refresh; TypeScript and ESLint passed.
