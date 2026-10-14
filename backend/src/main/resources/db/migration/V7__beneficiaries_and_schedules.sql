CREATE TABLE beneficiaries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    destination_account UUID NOT NULL REFERENCES accounts(id),
    name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DELETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX beneficiaries_active_unique ON beneficiaries(user_id,destination_account) WHERE status='ACTIVE';
CREATE INDEX beneficiaries_user_idx ON beneficiaries(user_id,status);
CREATE TABLE scheduled_transfers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    source_account UUID NOT NULL REFERENCES accounts(id),
    beneficiary_id UUID NOT NULL REFERENCES beneficiaries(id),
    amount NUMERIC(18,2) NOT NULL CHECK (amount>0),
    description VARCHAR(160) NOT NULL,
    frequency VARCHAR(16) NOT NULL CHECK (frequency IN ('ONCE','DAILY','WEEKLY','MONTHLY')),
    anchor_day INTEGER NOT NULL CHECK (anchor_day BETWEEN 1 AND 31),
    next_run_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','PAUSED','COMPLETED','CANCELLED','FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    last_result VARCHAR(16),
    last_error VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX scheduled_due_idx ON scheduled_transfers(next_run_at,next_attempt_at) WHERE status='ACTIVE';
CREATE INDEX scheduled_user_idx ON scheduled_transfers(user_id,created_at DESC);
CREATE TABLE scheduled_occurrences (
    schedule_id UUID NOT NULL REFERENCES scheduled_transfers(id),
    due_at TIMESTAMPTZ NOT NULL,
    transaction_id UUID NOT NULL UNIQUE REFERENCES transactions(id),
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(schedule_id,due_at)
);
