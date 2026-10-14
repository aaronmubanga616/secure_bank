# Security model

SecureBank is an educational simulator. Treat local test data as fictional. Do not expose the development stack to the public internet or use it for real funds.

## Foundation

- Private API routes deny access until authentication is configured.
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

Later stages add bearer sessions, refresh rotation, role and ownership enforcement, encrypted TOTP secrets, rate limiting, immutable journals, and security audit events. Their actual behavior and verification are recorded as those stages land.
