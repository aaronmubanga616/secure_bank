CREATE SEQUENCE account_number_sequence START 1000000001;
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    account_number VARCHAR(20) NOT NULL UNIQUE DEFAULT ('SB' || nextval('account_number_sequence')::text),
    type VARCHAR(16) NOT NULL CHECK (type IN ('SAVINGS','CURRENT')),
    currency CHAR(3) NOT NULL DEFAULT 'ZMW' CHECK (currency='ZMW'),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','FROZEN','SUSPENDED','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id,type)
);
CREATE INDEX accounts_user_idx ON accounts(user_id);
