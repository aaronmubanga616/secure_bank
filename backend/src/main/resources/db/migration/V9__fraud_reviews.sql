CREATE TABLE fraud_rules (
    id SMALLINT PRIMARY KEY CHECK(id=1),
    large_amount NUMERIC(18,2) NOT NULL DEFAULT 25000 CHECK(large_amount>0),
    velocity_count INTEGER NOT NULL DEFAULT 3 CHECK(velocity_count BETWEEN 2 AND 100),
    hold_score INTEGER NOT NULL DEFAULT 71 CHECK(hold_score BETWEEN 31 AND 100)
);
INSERT INTO fraud_rules(id) VALUES(1);
CREATE TABLE fraud_alerts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    transaction_id UUID REFERENCES transactions(id),
    score INTEGER NOT NULL CHECK(score BETWEEN 0 AND 100),
    reasons TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','RESOLVED','FALSE_POSITIVE','CONFIRMED_FRAUD')),
    review_note VARCHAR(500),
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(transaction_id)
);
CREATE INDEX fraud_status_idx ON fraud_alerts(status,created_at DESC);
CREATE TABLE user_devices (
    user_id UUID NOT NULL REFERENCES users(id),
    device_hash CHAR(64) NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(user_id,device_hash)
);
