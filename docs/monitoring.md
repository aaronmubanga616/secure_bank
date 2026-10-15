# Monitoring and operations

Run `node scripts/setup-env.mjs` to generate the private monitoring token file, then `docker compose --profile monitoring up -d --build`. Prometheus is at port 9090; Grafana is at port 3000, with username `admin` and the private `GRAFANA_ADMIN_PASSWORD` from `.env`. Both bind loopback. The automatically provisioned dashboard is **SecureBank · Operations**.

Prometheus scrapes the backend internally using a dedicated bearer token mounted as a Compose secret. Customer and staff session tokens cannot substitute for this credential. It is never embedded in the Grafana dashboard or public configuration. The configuration follows [Prometheus authorization settings](https://prometheus.io/docs/prometheus/latest/configuration/configuration/) and [Grafana file provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/).

The dashboard covers request rate/status, p95 latency, JVM memory, database connections, durable transaction counts, open investigations, pending/parked mail, due schedules, event age/backlog, PostgreSQL/Redis/Kafka availability, and consumer lag. Banking samples refresh every fifteen seconds. They are observational snapshots; financial decisions always use PostgreSQL. A lag value of -1 means measurement was unavailable. A stale sample timestamp exposes failed sampling.

Alert rules flag unavailable API scraping, delayed event publication, consumer backlog/unavailable lag, parked email, stale samples, server error rate, and database pool pressure. These rules are visible in Prometheus; external paging requires configuring your own Alertmanager/notification receiver. No external notifications are sent by this repository.

Use `/actuator/health/liveness` and `/actuator/health/readiness` for probes. Anonymous health responses expose status only; staff roles can see component detail. Kafka failure degrades overall health, while committed outbox events remain durable. Do not equate an asynchronous notification delay with a financial failure: fetch the transaction receipt and inspect its journal before taking action.

Investigate DLT messages by event/correlation ID. Fix the cause before replaying the original envelope onto `securebank.events`; preserve its event ID so successful consumers deduplicate it. Do not delete consumer claims or edit ledger rows. A DEAD email needs operator review; only requeue after confirming SMTP is healthy, and expect possible duplicate delivery after acknowledgement failures. Verification/reset tokens remain single-use.
