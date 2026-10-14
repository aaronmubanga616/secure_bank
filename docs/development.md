# Development plan

## Scope

Build a portfolio banking simulator with working customer, mobile, and staff applications. No real banks, real money, or payment-card credentials are involved.

## Stages

1. [x] Repository, shared contracts, three app foundations, Spring Boot, database migrations, and containers.
2. [x] Registration, verification, password reset, JWT sessions, role checks, TOTP, and rate limiting.
3. [x] Customer profiles and savings/current accounts with ownership checks.
4. Authoritative double-entry journal and database constraints.
5. Simulated deposits, withdrawals, and internal transfers.
6. Concurrent spending protection, idempotency, and configurable limits.
7. Beneficiaries and recurring transfers with duplicate prevention.
8. Transactional event outbox, Kafka delivery, consumers, and dead letters.
9. Fraud rules, held transfers, and staff investigation.
10. Notifications and downloadable PDF statements.
11. Customer web flows and responsive dashboard.
12. Native customer flows and secure device sessions.
13. Staff dashboard and backend role enforcement.
14. Security review and hardening.
15. Metrics, health checks, and monitoring dashboards.
16. Automated banking, authorization, concurrency, and frontend checks.
17. CI, operations documentation, and final verification.

## Working agreement

- Commit working, reviewable stages using the repository owner's configured Git identity.
- Keep passwords, tokens, and machine-specific tooling out of Git.
- Verify builds and relevant tests before marking stages complete.
- Record verification limitations honestly; configured CI is not proof that a check ran locally.
- Keep frontend money display separate from authoritative backend calculations.

## Toolchain choices

- Java 21 LTS and Spring Boot 3.5 use the stable Spring Security 6 and Hibernate APIs. See [Spring's supported toolchains](https://docs.spring.io/spring-boot/3.5/system-requirements.html).
- Expo SDK 57 targets React Native 0.86 and React 19.2.3. Native dependency versions must match [Expo's SDK matrix](https://docs.expo.dev/versions/v57.0.0/); use `npx expo install --check` inside `apps/mobile` after upgrades.
- npm workspaces share contracts and a lockfile. Vite builds the two browser applications independently.

On Windows, an emoji in a parent directory can break the native Java launcher. Clone into a path using ordinary letters if this occurs, or build the backend in Docker. Local verification may use a temporary copy of `backend` in an ASCII path; never copy secrets or commit machine-specific toolchains.
