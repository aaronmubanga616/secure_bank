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

## Profiles and accounts

- `GET /users/me` returns the current user's safe profile; `PATCH /users/me` updates `fullName`.
- `GET /accounts` lists only the current customer's accounts.
- `POST /accounts` accepts `type: SAVINGS | CURRENT`. This simulator permits one account of each type per customer.
- `GET /accounts/{id}` requires ownership. Foreign and missing IDs both return 404.

Account operations require CUSTOMER. Staff use explicit staff routes with their own role policies.

## Financial operations

`POST /transactions` creates a simulated transaction resource. Supply an `Idempotency-Key` header of 8-128 letters, digits, periods, underscores, colons, or hyphens. Use the same key when retrying the same intent. A different payload under the same key returns 409.

The body contains `type`, `amount` as a decimal string, and `description`. TRANSFER also needs `sourceAccount` and `destinationAccountNumber`; WITHDRAWAL needs only `sourceAccount`; DEPOSIT needs only the customer's own `destinationAccountNumber`. All amounts are ZMW and positive, with at most two decimal places.

A valid intent creates a resource with HTTP 201. **Check the returned `status`:** COMPLETED moves money; FAILED records a decline and moves none; FLAGGED means awaiting review and moves none. A failed intent remains failed when retried, even if the customer later adds funds. Correcting or trying a new intent requires a new key.

`GET /transactions` supports `page`, `size` (maximum 100), `query`, `status`, `from` (inclusive ISO timestamp), `to` (exclusive ISO timestamp), `minimum`, and `maximum`. Results include only transactions initiated by or involving the current customer. `GET /transactions/{id}` applies the same rule. Foreign account numbers in receipts are masked.

`GET /accounts/lookup?number=...` confirms an active recipient's display name and account type. Lookup is rate limited. `GET /transactions/limits` exposes current simulated limits. The banking day uses Africa/Lusaka; stored timestamps are UTC instants.

Administrators can `PATCH /admin/limits` with `perTransaction`, `dailyTransfer`, `dailyWithdrawal`, and `dailyDeposit`, each a positive decimal amount up to K1 billion. Customers, support agents, and compliance officers cannot change limits. Saved financial response bodies are immutable and replayed under the original customer/key; `GET /transactions/{id}` reads the current resource state.

## Recipients and schedules

`GET /beneficiaries` lists saved recipients. `POST /beneficiaries` accepts `name`, `accountNumber`, `password`, and `code` when MFA is enabled. `DELETE /beneficiaries/{id}` archives the recipient and cancels pending or paused schedules for that recipient. Saved recipients remain owner scoped; historical records are preserved.

`GET /scheduled-transfers` returns up to 200 schedules, with active/paused schedules first. `POST /scheduled-transfers` accepts `sourceAccount`, `beneficiaryId`, `amount`, `description`, `frequency` (ONCE, DAILY, WEEKLY, MONTHLY), `startsAt` (ISO timestamp within the next year), `password`, and MFA `code` when applicable. `PATCH /scheduled-transfers/{id}` accepts `status: ACTIVE | PAUSED | CANCELLED` for an unfinished schedule.

The worker executes due payments every fifteen seconds. Each occurrence has a deterministic financial idempotency key. Money commits independently of schedule bookkeeping, so a crash after a payment cannot cause another payment when the worker resumes. Transient technical failures retry up to three times with backoff. A business decline is a final financial result; recurring schedules advance to the next occurrence. A resumed overdue schedule runs once and skips missed slots. Monthly schedules preserve their original day, using the last day of shorter months. Cancellation stops future dispatch; it cannot undo money already committed by an in-flight occurrence.

## Error format

Errors contain `status`, `code`, `message`, `correlationId`, and optional `fieldErrors`. Unknown resources return 404; unauthorized actions return 403; invalid or expired sessions return 401; rate limits return 429. Unexpected errors never expose stack traces or submitted credentials.

Use the `X-Correlation-ID` response header when diagnosing failures. Supplied identifiers are restricted to 64 letters, digits, or hyphens.
