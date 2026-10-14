CREATE TABLE transaction_limits (
    id INTEGER PRIMARY KEY CHECK (id=1),
    per_transaction NUMERIC(18,2) NOT NULL CHECK (per_transaction BETWEEN 0.01 AND 1000000000),
    daily_transfer NUMERIC(18,2) NOT NULL CHECK (daily_transfer BETWEEN 0.01 AND 1000000000),
    daily_withdrawal NUMERIC(18,2) NOT NULL CHECK (daily_withdrawal BETWEEN 0.01 AND 1000000000),
    daily_deposit NUMERIC(18,2) NOT NULL CHECK (daily_deposit BETWEEN 0.01 AND 1000000000),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO transaction_limits(id,per_transaction,daily_transfer,daily_withdrawal,daily_deposit) VALUES (1,100000,200000,50000,200000);
CREATE TABLE idempotency_responses (
    user_id UUID NOT NULL REFERENCES users(id),
    idempotency_key VARCHAR(128) NOT NULL,
    transaction_id UUID NOT NULL UNIQUE REFERENCES transactions(id),
    response_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(user_id,idempotency_key),
    FOREIGN KEY(user_id,idempotency_key) REFERENCES transactions(user_id,idempotency_key)
);
CREATE TRIGGER idempotency_immutable BEFORE UPDATE OR DELETE ON idempotency_responses FOR EACH ROW EXECUTE FUNCTION reject_journal_mutation();
