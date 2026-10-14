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

## Ledger

Backend verification passed eight unit/HTTP tests and twelve integration tests. Ledger tests verify balanced transfers, exact derived balances, immutable entries and completed transactions, rejection of incomplete journals at commit, and atomic rollback of an overdraft. The tests use PostgreSQL's actual deferred constraints.

## Money movements

Backend verification passed eight unit/HTTP tests and seventeen integration tests. New coverage exercises deposits, transfers, withdrawals, receipt ownership, filtered incoming history, exact cents, duplicate retries, changed-request conflicts, persistent failed operations, missing idempotency headers, and rejection of additional fractional digits.

## Concurrent spending and limits

Backend verification passed eight unit/HTTP tests and twenty-two integration tests. Two simultaneous K800 withdrawals against K1,000 produced one completion and one decline, leaving K200. Eight simultaneous retries produced one transaction and two journal entries. Eight transfers from two accounts respected one shared daily limit. Tests also verify customer-scoped keys, immutable saved responses, and denial of limit changes by customer/support/compliance roles.

## Recipients and schedules

Backend verification passed ten unit/HTTP tests and twenty-seven integration tests. New tests cover month-end/leap-year recurrence, recipient and schedule ownership, password confirmation, cancellation on recipient deletion, two simultaneous workers, and recovery after money committed before schedule bookkeeping. A focused schedule verification also runs after improving lookups for older schedule records.
