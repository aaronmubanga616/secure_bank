# API guide

Base URL: `http://localhost:8180/api/v1` for local development. The gateways proxy `/api` to the same backend. Local OpenAPI is at `/v3/api-docs` when `API_DOCS_ENABLED=true`.

## Authentication

| Method | Path | Body / purpose |
| --- | --- | --- |
| POST | `/auth/register` | `email`, `fullName`, `password`; always returns a generic 202 response |
| POST | `/auth/request-verification` | `email`; request a replacement verification link |
| POST | `/auth/verify-email` | `token`; one-use email verification |
| POST | `/auth/login` | `email`, `password`; session or an MFA challenge |
| POST | `/auth/mfa/verify` | `challenge`, `code`; TOTP or a one-use recovery code |
| POST | `/auth/refresh` | `refreshToken`; rotate and return a new session |
| POST | `/auth/logout` | `refreshToken`; revoke the family and invalidate access |
| POST | `/auth/forgot-password` | `email`; generic 202 response |
| POST | `/auth/reset-password` | `token`, `password`; one-use reset and session invalidation |
| POST | `/auth/change-password` | Bearer + `currentPassword`, `newPassword` |
| POST | `/auth/mfa/setup` | Bearer + `password`; secret and authenticator URI |
| POST | `/auth/mfa/confirm` | Bearer + `code`; enables MFA and returns recovery codes once |
| POST | `/auth/mfa/disable` | Bearer + `password`, `code` |

Access tokens last 10 minutes. Refresh tokens last 7 days and rotate on every use. Both are opaque credentials to clients; do not log them. A successful session contains `accessToken`, `refreshToken`, and a safe `user` object. An MFA challenge contains `mfaRequired: true` and `challenge`, with no usable session token.

Send `Authorization: Bearer <accessToken>` to protected resources. Token validation checks the signature, issuer, audience, expiry, current role, account status, verification status, and session version. Password changes, logout, MFA changes, and refresh replay invalidate existing access tokens.

Tokens are returned in JSON and are not cookies. Browser clients keep them in memory; reloading the page requires signing in again. Native clients use device secure storage for refresh credentials and keep short-lived access tokens in memory. This avoids ambient-cookie authorization and does not require disabling a cookie CSRF defense.

Local email is captured by [Mailpit](http://localhost:8025). `docker compose up -d mailpit` starts the inbox. Security links carry tokens in a URL fragment so browser request logs and Referer headers do not receive them. Native users can copy the token into their verification/reset screen.

## Errors

## Profiles and accounts

- `GET /users/me` returns the current user's safe profile; `PATCH /users/me` updates `fullName`.
- `GET /accounts` lists only the current customer's accounts.
- `POST /accounts` accepts `type: SAVINGS | CURRENT`. This simulator permits one account of each type per customer.
- `GET /accounts/{id}` requires ownership. Foreign and missing IDs both return 404.

Account operations require CUSTOMER. Staff use explicit staff routes with their own role policies.

## Error format

Errors contain `status`, `code`, `message`, `correlationId`, and optional `fieldErrors`. Unknown resources return 404; unauthorized actions return 403; invalid or expired sessions return 401; rate limits return 429. Unexpected errors never expose stack traces or submitted credentials.

Use the `X-Correlation-ID` response header when diagnosing failures. Supplied identifiers are restricted to 64 letters, digits, or hyphens.
