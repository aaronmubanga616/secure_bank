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

| Package | Advisory | State at review |
| --- | --- | --- |
| braces | [Nested pattern stack exhaustion](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) | npm's latest 3.0.3 remains affected; used by build/watch tooling |
| node-forge | [RSA signature parsing](https://github.com/advisories/GHSA-86w9-cpqp-85rv) | npm's latest 1.4.0 remains affected; Expo development/certificate tooling |
| decode-uri-component | [Malformed URL decoding](https://github.com/advisories/GHSA-vcc3-ghjq-m6fr) | Fix changes from CommonJS to ESM; requires an upstream Expo Router dependency update |
| uuid | [Optional output buffer bounds](https://github.com/advisories/GHSA-w5hq-g745-h8pq) | xcode tooling uses the v4 API; review a compatible 11.1.1 override |

Do not run `npm audit fix --force` blindly: its proposed Expo 44 downgrade breaks the supported native stack. Builds passing do not resolve these advisories. Recheck the lockfile and upstream packages before releasing native builds or exposing development servers.

## Sessions and MFA

- Only a verified, active user can receive a session. Public registration always assigns the CUSTOMER role.
- JWTs are signed with HS256 using at least 32 random bytes. Issuer and audience are fixed; other algorithms are rejected.
- Refresh tokens and email tokens are stored only as SHA-256 hashes. Refresh rotation locks the user before the token row. Reuse revokes the family and increments the user's session version.
- Access-token validation reads current user state, so logout and security changes take effect immediately.
- Redis fixed-window counters use an atomic increment-and-expiry script. Security checks fail closed if the counter store is unavailable. Login attempts lock an account's login path temporarily after ten attempts in fifteen minutes.
- TOTP uses the RFC 6238 SHA-1 algorithm with a 30-second step and one step of clock tolerance. AES-256-GCM encrypts enrolled secrets. Temporary setup state is also encrypted, with a ten-minute expiry.
- A TOTP step cannot be used twice. Recovery codes are random, hashed, shown once, and consumed transactionally.
- MFA changes require recent password knowledge. Disabling MFA also requires a valid TOTP or recovery code. Password resets do not disable MFA.
- Security emails are delivered through an abstraction after database commit. An unavailable SMTP server requires requesting a new link; durable event retries are added with the outbox stage.

Automated integration checks cover verification-token reuse, refresh replay, logout invalidation, MFA replay, recovery-code reuse, login lockout, and password-reset invalidation using PostgreSQL and Redis containers.

Later stages add account ownership checks, immutable journals, and fraud investigation. Their actual behavior and verification are recorded as those stages land.
