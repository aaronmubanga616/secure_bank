CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    reference VARCHAR(40) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(16) NOT NULL CHECK (type IN ('DEPOSIT','WITHDRAWAL','TRANSFER')),
    amount NUMERIC(18,2) NOT NULL CHECK (amount>0),
    currency CHAR(3) NOT NULL DEFAULT 'ZMW' CHECK (currency='ZMW'),
    source_account UUID REFERENCES accounts(id),
    destination_account UUID REFERENCES accounts(id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED','REVERSED','FLAGGED')),
    description VARCHAR(160) NOT NULL,
    risk_score INTEGER NOT NULL DEFAULT 0 CHECK (risk_score BETWEEN 0 AND 100),
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    failure_reason VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    UNIQUE(user_id,idempotency_key),
    CHECK (
        (type='DEPOSIT' AND source_account IS NULL AND destination_account IS NOT NULL) OR
        (type='WITHDRAWAL' AND source_account IS NOT NULL AND destination_account IS NULL) OR
        (type='TRANSFER' AND source_account IS NOT NULL AND destination_account IS NOT NULL AND source_account<>destination_account)
    )
);
CREATE INDEX transactions_user_date_idx ON transactions(user_id,created_at DESC,id);
CREATE INDEX transactions_source_date_idx ON transactions(source_account,created_at DESC);
CREATE INDEX transactions_destination_date_idx ON transactions(destination_account,created_at DESC);
CREATE INDEX transactions_status_idx ON transactions(status,created_at DESC);

CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    bank_account_id UUID UNIQUE REFERENCES accounts(id),
    code VARCHAR(64) NOT NULL UNIQUE,
    currency CHAR(3) NOT NULL DEFAULT 'ZMW' CHECK (currency='ZMW'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (bank_account_id IS NOT NULL OR code='SIMULATED_CASH')
);
INSERT INTO ledger_accounts(id,code) VALUES ('00000000-0000-0000-0000-000000000001','SIMULATED_CASH');
INSERT INTO ledger_accounts(id,bank_account_id,code)
    SELECT gen_random_uuid(),id,'CUSTOMER:' || id::text FROM accounts;

CREATE FUNCTION create_customer_ledger_account() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO ledger_accounts(id,bank_account_id,code) VALUES (gen_random_uuid(),NEW.id,'CUSTOMER:' || NEW.id::text);
    RETURN NEW;
END;
$$;
CREATE TRIGGER account_ledger_created AFTER INSERT ON accounts FOR EACH ROW EXECUTE FUNCTION create_customer_ledger_account();

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    ledger_account_id UUID NOT NULL REFERENCES ledger_accounts(id),
    direction VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
    amount NUMERIC(18,2) NOT NULL CHECK (amount>0),
    currency CHAR(3) NOT NULL DEFAULT 'ZMW' CHECK (currency='ZMW'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(transaction_id,direction)
);
CREATE INDEX ledger_entries_balance_idx ON ledger_entries(ledger_account_id) INCLUDE(direction,amount);
CREATE INDEX ledger_entries_transaction_idx ON ledger_entries(transaction_id);

CREATE FUNCTION reject_journal_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Posted ledger entries are immutable' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER ledger_immutable BEFORE UPDATE OR DELETE ON ledger_entries FOR EACH ROW EXECUTE FUNCTION reject_journal_mutation();
CREATE TRIGGER ledger_no_truncate BEFORE TRUNCATE ON ledger_entries FOR EACH STATEMENT EXECUTE FUNCTION reject_journal_mutation();

CREATE FUNCTION protect_transaction_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' OR OLD.status IN ('COMPLETED','REVERSED') THEN
        RAISE EXCEPTION 'Completed transaction history is immutable' USING ERRCODE='23514';
    END IF;
    IF NEW.id<>OLD.id OR NEW.user_id<>OLD.user_id OR NEW.type<>OLD.type OR NEW.amount<>OLD.amount
        OR NEW.currency<>OLD.currency OR NEW.source_account IS DISTINCT FROM OLD.source_account
        OR NEW.destination_account IS DISTINCT FROM OLD.destination_account OR NEW.idempotency_key<>OLD.idempotency_key
        OR NEW.request_hash<>OLD.request_hash OR NEW.reference<>OLD.reference OR NEW.created_at<>OLD.created_at THEN
        RAISE EXCEPTION 'Financial transaction identity is immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER transaction_history BEFORE UPDATE OR DELETE ON transactions FOR EACH ROW EXECUTE FUNCTION protect_transaction_history();

CREATE FUNCTION validate_transaction_journal() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    tx_id UUID;
    tx transactions%ROWTYPE;
    lines INTEGER;
    debits NUMERIC(18,2);
    credits NUMERIC(18,2);
    expected_debit UUID;
    expected_credit UUID;
BEGIN
    IF TG_TABLE_NAME='transactions' THEN tx_id:=NEW.id; ELSE tx_id:=NEW.transaction_id; END IF;
    SELECT * INTO tx FROM transactions WHERE id=tx_id;
    SELECT count(*),coalesce(sum(amount) FILTER(WHERE direction='DEBIT'),0),coalesce(sum(amount) FILTER(WHERE direction='CREDIT'),0)
        INTO lines,debits,credits FROM ledger_entries WHERE transaction_id=tx_id;
    IF tx.status='COMPLETED' THEN
        IF lines<>2 OR debits<>credits OR debits<>tx.amount OR tx.completed_at IS NULL THEN
            RAISE EXCEPTION 'A completed transaction requires a balanced journal matching its amount' USING ERRCODE='23514';
        END IF;
        IF tx.type='DEPOSIT' THEN
            SELECT id INTO expected_debit FROM ledger_accounts WHERE code='SIMULATED_CASH';
        ELSE SELECT id INTO expected_debit FROM ledger_accounts WHERE bank_account_id=tx.source_account; END IF;
        IF tx.type='WITHDRAWAL' THEN
            SELECT id INTO expected_credit FROM ledger_accounts WHERE code='SIMULATED_CASH';
        ELSE SELECT id INTO expected_credit FROM ledger_accounts WHERE bank_account_id=tx.destination_account; END IF;
        IF expected_debit IS NULL OR expected_credit IS NULL OR NOT EXISTS (
            SELECT 1 FROM ledger_entries WHERE transaction_id=tx_id AND direction='DEBIT' AND ledger_account_id=expected_debit
        ) OR NOT EXISTS (
            SELECT 1 FROM ledger_entries WHERE transaction_id=tx_id AND direction='CREDIT' AND ledger_account_id=expected_credit
        ) THEN
            RAISE EXCEPTION 'Journal accounts do not match the transaction' USING ERRCODE='23514';
        END IF;
        -- Serialize balance checks in ledger ID order, including writes made outside the application service.
        PERFORM id FROM ledger_accounts WHERE id IN (expected_debit,expected_credit) ORDER BY id FOR UPDATE;
        IF EXISTS (
            SELECT 1 FROM ledger_accounts l JOIN ledger_entries e ON e.ledger_account_id=l.id
            WHERE l.bank_account_id IS NOT NULL AND l.id IN (expected_debit,expected_credit)
            GROUP BY l.id HAVING sum(CASE WHEN e.direction='CREDIT' THEN e.amount ELSE -e.amount END)<0
        ) THEN RAISE EXCEPTION 'Customer accounts cannot be overdrawn' USING ERRCODE='23514'; END IF;
    ELSIF lines<>0 THEN
        RAISE EXCEPTION 'Only completed transactions can have journal entries' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE CONSTRAINT TRIGGER transaction_journal_valid AFTER INSERT OR UPDATE ON transactions
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_transaction_journal();
CREATE CONSTRAINT TRIGGER entry_journal_valid AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_transaction_journal();

CREATE VIEW account_balances AS
    SELECT a.id AS account_id,coalesce(sum(CASE WHEN e.direction='CREDIT' THEN e.amount ELSE -e.amount END),0)::NUMERIC(18,2) AS balance
    FROM accounts a LEFT JOIN ledger_accounts l ON l.bank_account_id=a.id LEFT JOIN ledger_entries e ON e.ledger_account_id=l.id GROUP BY a.id;
