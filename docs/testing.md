# Testing SecureBank

## Automated checks

```sh
npm ci
npm run format:check
npm run typecheck
npm run lint
npm test
npm run check:toolchain
npm run security:check
npm run build
npm run build:mobile
cd backend
mvn -B -ntp -s .mvn/settings.xml clean verify
```

Java verification includes Google Java Format checks, JUnit, Spring HTTP/security tests, and Testcontainers tests using real PostgreSQL, Redis and Kafka. Docker must be available; integration tests are not silently replaced with in-memory storage. JaCoCo writes `backend/target/site/jacoco/index.html`. Run `mvn spotless:apply` inside `backend` to format Java and `npm run format` from the root for other source/documents.

Tests cover journal balance/immutability, rollback, exact cents, account ownership, incoming history, simultaneous overspending, shared daily limits, identical concurrent retries, changed-payload conflicts, refresh replay/logout races, MFA/staff roles, recipient step-up, recurrence/month ends, schedule crash recovery, outbox atomicity, duplicate consumers, Kafka dead letters, encrypted mail retries, held-payment approval, PDF pagination and private metrics. Shared JavaScript tests exercise exact-money helpers, date ranges, coordinated refresh, payment retry keys, secure native storage and logout during biometric enrollment/unlock.

## Live browser and load checks

Start the [container stack](deployment.md), enable the private test administrator and leave that dedicated test account available for the staff journey. The first run enrolls its authenticator and saves the private test secret under ignored `.local`; do not reuse this account as a real administrator or publish test traces. The browser helper creates fresh fictional customers and uses the local Mailpit inbox.

```sh
# Set WEB_URL=http://localhost:8088 and ADMIN_URL=http://localhost:8089 in your shell.
npm run test:e2e
npm run test:performance
node --env-file=.env scripts/check-monitoring.mjs
```

Install Playwright Chromium with `npx playwright install chromium`, or set `PW_CHANNEL=msedge` to use installed Edge. The three browser journeys cover customer registration/email/login/accounts/payment/receipt/PDF/inbox, a declined withdrawal, and staff MFA/review/freeze/audit. They capture responsive desktop/phone examples. Reports and traces stay ignored because they may contain session data. Use only disposable simulator accounts. Repeated staff runs must use a fresh 30-second authenticator step.

The bounded load script uses ten workers for 100 unique deposits, then ten concurrent submissions of one additional intent. It asserts exactly 101 transactions, one duplicate receipt and an exact K102.00 balance, and fails if p95 exceeds five seconds. It only permits a local API host. Its JSON report contains timings and counts, without passwords or session credentials. This small consistency exercise does not establish production capacity.

## Native validation

Expo exports both Android and iOS Hermes bundles. `npx expo install --check` inside `apps/mobile` verifies SDK dependency compatibility. See [mobile](mobile.md) for EAS/native setup and the Maestro flow. Exports and mocked SecureStore tests do not verify OS keychain prompts, device biometrics, deep links, background behavior or signed app packaging. Complete the device checklist before presenting the native app as device tested.

## Continuous integration

GitHub Actions runs frontend checks/bundles, a clean backend verification with coverage, and complete container browser/load/monitoring checks on every push and pull request. Test infrastructure and random credentials are disposable. Only backend coverage is uploaded; `.env`, `.local`, traces, inbox contents and screenshots are excluded from public artifacts. Weekly Dependabot updates cover npm, Maven and Actions. Review the documented upstream advisory baseline rather than assuming a passing baseline check means no advisories exist.

Measured local results and known verification limits are kept in [the verification record](verification.md).
