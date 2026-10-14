CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    user_id UUID REFERENCES users(id),
    payload JSONB NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX outbox_pending_idx ON outbox_events(next_attempt_at,created_at) WHERE published_at IS NULL;
CREATE TABLE consumed_events (
    consumer VARCHAR(64) NOT NULL,
    event_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(consumer,event_id)
);
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    event_id UUID NOT NULL,
    title VARCHAR(120) NOT NULL,
    message VARCHAR(500) NOT NULL,
    category VARCHAR(32) NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id,event_id)
);
CREATE INDEX notifications_user_idx ON notifications(user_id,created_at DESC);
CREATE TABLE audit_projections (
    event_id UUID PRIMARY KEY,
    audit_id UUID NOT NULL REFERENCES audit_logs(id),
    action VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    resource VARCHAR(160) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE mail_queue (
    id UUID PRIMARY KEY,
    recipient VARCHAR(254) NOT NULL,
    subject VARCHAR(160) NOT NULL,
    encrypted_body TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','SENT','DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX mail_pending_idx ON mail_queue(next_attempt_at) WHERE status='PENDING';
