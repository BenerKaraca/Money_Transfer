CREATE TABLE customers (
    id uuid PRIMARY KEY,
    full_name varchar(120) NOT NULL,
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE accounts (
    id uuid PRIMARY KEY,
    customer_id uuid NOT NULL REFERENCES customers(id),
    currency varchar(3) NOT NULL,
    balance numeric(19,2) NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT accounts_balance_nonnegative CHECK (balance >= 0),
    CONSTRAINT accounts_currency_try CHECK (currency = 'TRY')
);
CREATE INDEX accounts_customer_idx ON accounts(customer_id);

CREATE TABLE transfers (
    id uuid PRIMARY KEY,
    idempotency_key varchar(100) NOT NULL UNIQUE,
    source_account_id uuid NOT NULL REFERENCES accounts(id),
    destination_account_id uuid NOT NULL REFERENCES accounts(id),
    amount numeric(19,2) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT transfers_distinct_accounts CHECK (source_account_id <> destination_account_id),
    CONSTRAINT transfers_positive_amount CHECK (amount > 0)
);
CREATE INDEX transfers_source_created_idx ON transfers(source_account_id, created_at DESC);
CREATE INDEX transfers_destination_created_idx ON transfers(destination_account_id, created_at DESC);

CREATE TABLE ledger_entries (
    id uuid PRIMARY KEY,
    transfer_id uuid NOT NULL REFERENCES transfers(id),
    account_id uuid NOT NULL REFERENCES accounts(id),
    direction varchar(6) NOT NULL,
    amount numeric(19,2) NOT NULL,
    balance_after numeric(19,2) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ledger_direction_check CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ledger_amount_positive CHECK (amount > 0),
    CONSTRAINT ledger_transfer_account_unique UNIQUE (transfer_id, account_id)
);
CREATE INDEX ledger_account_created_idx ON ledger_entries(account_id, created_at DESC);
