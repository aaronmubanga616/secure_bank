# Security model

SecureBank is an educational simulator. Treat local test data as fictional. Do not expose the development stack to the public internet or use it for real funds.

## Foundation

- Private API routes require a valid, current bearer session; staff routes enforce staff roles on the backend.
- Password hashing uses BCrypt with a work factor of 12.
- Backend responses carry security headers and a sanitized correlation identifier.
- Browser CORS origins are explicitly configured; wildcard credentialed origins are not allowed.
- Database schemas are managed by Flyway. Hibernate never creates or updates production tables.
- Service ports are bound to the loopback interface for local development.
- Random development secrets are generated locally and excluded from Git.
- Browser and API containers use restrictive content security policies. TLS termination must be configured before any remote deployment.

## Dependency advisory review

The initial npm audit on 2026-10-14 identified upstream issues in the Expo/native development dependency tree:

| Package              | Advisory                                                                             | State at review                                                                      |
| -------------------- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------ |
| braces               | [Nested pattern stack exhaustion](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) | npm's latest 3.0.3 remains affected; used by build/watch tooling                     |
| node-forge           | [RSA signature parsing](https://github.com/advisories/GHSA-86w9-cpqp-85rv)           | npm's latest 1.4.0 remains affected; Expo development/certificate tooling            |
| decode-uri-component | [Malformed URL decoding](https://github.com/advisories/GHSA-vcc3-ghjq-m6fr)          | Fix changes from CommonJS to ESM; requires an upstream Expo Router dependency update |
| uuid                 | [Optional output buffer bounds](https://github.com/advisories/GHSA-w5hq-g745-h8pq)   | Patched with an 11.1.1 override; Xcode's actual UUID generator was checked           |

Do not run `npm audit fix --force` blindly: its proposed Expo 44 downgrade breaks the supported native stack. Builds passing do not resolve these advisories. Recheck the lockfile and upstream packages before releasing native builds or exposing development servers.

On 2026-10-15, the patched lockfile reports three direct upstream advisories, propagated to 24 package findings (22 high, two moderate). The reviewed baseline is explicit in `scripts/dependency-baseline.json`; `npm run security:check` fails on new advisory IDs or changed affected versions. This is a documented limitation of the educational simulator, not a clean security certification. In particular, malformed native deep-link decoding remains affected until an Expo-compatible upstream migration is available.

## Sessions and MFA

- Only a verified, active user can receive a session. Public registration always assigns the CUSTOMER role.
- JWTs are signed with HS256 using at least 32 random bytes. Issuer and audience are fixed; other algorithms are rejected.
- Refresh tokens and email tokens are stored only as SHA-256 hashes. Refresh rotation locks the user before the token row. Reuse revokes the family and increments the user's session version.
- Access-token validation reads current user state, so logout and security changes take effect immediately.
- Redis fixed-window counters use an atomic increment-and-expiry script. Security checks fail closed if the counter store is unavailable. Login attempts lock an account's login path temporarily after ten attempts in fifteen minutes.
- TOTP uses the RFC 6238 SHA-1 algorithm with a 30-second step and one step of clock tolerance. AES-256-GCM encrypts enrolled secrets. Temporary setup state is also encrypted, with a ten-minute expiry.
- A TOTP step cannot be used twice. Recovery codes are random, hashed, shown once, and consumed transactionally.
- MFA changes require recent password knowledge. Disabling MFA also requires a valid TOTP or recovery code. Password resets do not disable MFA.
- Staff operations require a signed authenticator-verified session as well as the correct role. A password-only staff session can enroll an authenticator but cannot use staff operations.
- MFA challenges include the current session version; password changes invalidate pending challenges. Logout revokes a token family even if a concurrent refresh already rotated the supplied token.
- Authenticated API traffic is bounded at 120 requests per user per minute, in addition to login, email, MFA, recipient-lookup, and step-up limits. Security counters fail closed.
- Security email jobs commit with the initiating action. AES-GCM encrypts message bodies with separate associated data. SMTP delivery retries five times, then parks the job. Events use a transactional outbox and idempotent Kafka consumers.

Automated integration checks cover verification-token reuse, refresh replay, logout invalidation, MFA replay, recovery-code reuse, login lockout, and password-reset invalidation using PostgreSQL and Redis containers.

## Financial and operational boundaries

PostgreSQL's journal is authoritative. Customer account reads, history, recipients, notifications, schedules, receipts, and statements enforce ownership. Transfer rows lock users and accounts in deterministic order; deferred database constraints verify exact balanced entries and prevent overdrafts. Posted entries, completed transaction identity, saved idempotency responses, and audit history are immutable.

Identical submissions reuse a request key across review edits on both clients. High-risk transfers have no journal while held. Staff approval rechecks state, funds, and limits; it cannot release a held transfer twice. Suspension revokes sessions and pauses schedules. Staff access changes require a reason. No API creates real money, connects a bank, or accepts card credentials.

Redis display statistics expire after five seconds and never determine a balance or financial limit. Fraud counters are short-lived hints backed by durable PostgreSQL activity. Metrics use a separate monitoring credential; no customer identifiers, tokens, or secrets appear in exported labels. Private API responses use `Cache-Control: no-store`.

Browser tokens remain in memory. Native refresh tokens use SecureStore and can be migrated into biometric-protected storage. Access tokens and private query data remain in memory. See [native behavior and device checks](../mobile.md). Deployment requires TLS, private infrastructure networks, backups, secret rotation, least-privilege database roles, and review of the known upstream advisories; see [deployment](../deployment.md).
