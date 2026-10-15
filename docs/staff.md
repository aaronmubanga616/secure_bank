# Staff operations

The separate staff portal runs on port 5174 during development or 8089 in Compose. The API enforces roles for every operation:

| Operation                               | Administrator | Support | Compliance |
| --------------------------------------- | ------------- | ------- | ---------- |
| Customer/transaction search and details | Yes           | Yes     | Yes        |
| Audit trail and health overview         | Yes           | Yes     | Yes        |
| Fraud investigations and decisions      | Yes           | No      | Yes        |
| View fraud policy                       | Yes           | No      | Yes        |
| Suspend customers or freeze accounts    | Yes           | No      | No         |
| Change limits/rules or create staff     | Yes           | No      | No         |

For the first administrator, set `BOOTSTRAP_ADMIN_ENABLED=true`, `BOOTSTRAP_ADMIN_EMAIL`, and a unique `BOOTSTRAP_ADMIN_PASSWORD` (12+ characters, at most 72 UTF-8 bytes) in your private environment before starting the API. The helper generates a random staff password in new local `.env` files. Provisioning creates one verified administrator only when no administrator exists; it never resets an existing password. Disable the flag after the initial setup. No account is created by default and no public seed credentials exist.

Sign in on the staff portal and enroll an authenticator in My security. Save the recovery codes, then sign in again using the authenticator challenge. An administrator can create support/compliance accounts in Configuration. Share initial credentials privately; each staff member should change their password and enroll their own authenticator. Public registration always creates a customer.

Suspension revokes existing sessions and pauses active schedules. Reinstatement requires a new sign-in; paused schedules stay paused until the customer resumes them. Freezing preserves the ledger and blocks new movement through that account. Access changes require a reason, which is stored alongside an immutable audit event. Customer detail and transaction inspection are audited too.

Fraud decisions record the actor, note, outcome, and time. Resolution/false-positive approves a held transfer after rechecking state, funds, and limits. Confirmed fraud rejects a held transfer. Investigations on completed transactions never rewrite their posted entries.
