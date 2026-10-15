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

## Events and fraud

Backend verification passed ten unit/HTTP tests and thirty-one integration tests after the event stage, including a real Kafka container, poison-message delivery, duplicate suppression, atomic rollback, and encrypted mail retries. A focused fraud/payment run passed twelve integration tests. Additional focused fraud and statement verification passed four integration tests; held transfers have no journal until approval and cannot be released twice.

## Statements and customer web

PDF tests verify exact posted opening/closing balances, masked identifiers, and owner-only access. A rendered statement was visually inspected. Notification tests prove owner-only read changes and duplicate suppression.

Two live Playwright journeys passed against the running API, PostgreSQL, Redis, Kafka, and Mailpit: registration, email verification, login, account opening, deposit review/receipt, dashboard balance, mobile navigation, a PDF download, asynchronous notification delivery, and an insufficient-funds withdrawal. Desktop and 390-pixel screenshots were reviewed; phone layout has no document overflow. The browser run exposed and corrected duplicate React versions, select-label accessibility, and an absolute-positioned table label escaping its scroll container.

Shared-client tests passed four checks. Browser tokens stay in memory; reloading requires sign-in. The only persisted browser value is a random, non-secret device hint.

## Native customer app

Android and iOS Hermes exports passed with the complete native routes. Expo reports compatible dependencies. Six JavaScript tests passed, including two native session tests covering refresh-only persistence, the authorization gate while locked, and logout during an in-flight unlock. Type checks and ESLint passed. No Android/iOS native build or physical biometric test has been performed on this Windows machine.

## Staff operations

The complete backend suite passed 50 tests (10 unit/HTTP and 40 integration), without failures or skips. Later focused verification passed ten integration checks covering account-scoped history, staff roles/MFA, suspension, immutable audit history, private response caching, Unicode password limits, metrics credentials, and request throttling. The live staff browser journey passed authenticator enrollment/sign-in, release of a held transfer, account freezing, and audit lookup. Both browser production builds and type/lint checks passed.

## Client hardening

Ten JavaScript tests passed after adding exact input normalization, inclusive Lusaka date boundaries, stable retry keys across unchanged review edits, fresh keys after acknowledged payments, and cancellation of biometric enrollment on logout. TypeScript and lint passed. The patched UUID API check passed. Dependency review reports three explicitly documented upstream advisories; new advisories or changed affected versions fail the baseline check. The bounded local payment load check also passed; final container measurements are recorded with the testing guide.

## Final integration and monitoring

On 2026-10-15 a clean backend verification passed **56 checks**: 10 unit/HTTP and 46 integration tests, with no failures or skips. JaCoCo generated a report from that clean run. Ten JavaScript tests, all workspace type checks, lint, both production browser builds, SDK compatibility and both Android/iOS Hermes exports passed. A transient Windows file-read failure during native export was resolved by clearing Metro's cache and limiting it to two workers.

All three live browser journeys passed through the Docker customer/staff gateways and packaged backend, with real PostgreSQL, Redis, Kafka and Mailpit. Desktop/390-pixel screenshots were inspected. The container load check used ten workers: 100 unique deposits in 2,639 ms, 37.9 operations/second, p50 237 ms, p95 **459 ms**, maximum 575 ms. Ten duplicate submissions then created one additional payment. The final exact balance was K102.00. These are local simulator measurements, not production capacity claims.

Private metrics authorization, a successful Prometheus scrape and the provisioned Grafana dashboard passed live checks. Local image downloads repeatedly timed out, so monitoring configuration was exercised using already available Prometheus 3.13.1 and Grafana 13.2.1; the committed Compose configuration requests newer 3.13.4/13.2.4 patches for CI. Grafana used local port 3100 because another application held 3000. The initial banking container build passed; a later rebuild using Maven Central and cached dependencies hit external connection timeouts and is also checked in CI.

The two-page PDF sample was rendered and inspected. Pagination preserves transaction/reference pairs and repeats account details, column labels and page numbers. Native device packaging/biometrics and the included Maestro flow remain unexecuted. The three documented upstream Expo advisories remain open.
