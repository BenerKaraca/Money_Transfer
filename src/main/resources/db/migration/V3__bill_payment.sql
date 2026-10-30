ALTER TABLE accounts ADD COLUMN reserved numeric(19,2) NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD CONSTRAINT accounts_reserved_nonnegative CHECK (reserved >= 0);
ALTER TABLE accounts ADD CONSTRAINT accounts_reserved_within_balance CHECK (reserved <= balance);

CREATE TABLE bill_payments (
    id uuid PRIMARY KEY,
    idempotency_key varchar(100) NOT NULL UNIQUE,
    account_id uuid NOT NULL REFERENCES accounts(id),
    amount numeric(19,2) NOT NULL,
    scenario varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT bill_payment_positive CHECK (amount > 0),
    CONSTRAINT bill_payment_status CHECK (status IN ('PENDING','UNKNOWN','SUCCEEDED','FAILED')),
    CONSTRAINT bill_payment_scenario CHECK (scenario IN ('APPROVE','REJECT','TIMEOUT_ONCE'))
);
CREATE INDEX bill_payments_pending_idx ON bill_payments(status, updated_at);

-- An internal clearing account receives completed bill payments.
INSERT INTO customers (id, full_name, created_at)
VALUES ('ffffffff-ffff-ffff-ffff-fffffffffff0', 'CoreBank Clearing', CURRENT_TIMESTAMP);
INSERT INTO accounts (id, customer_id, currency, balance, version, created_at, reserved)
VALUES ('ffffffff-ffff-ffff-ffff-fffffffffff1', 'ffffffff-ffff-ffff-ffff-fffffffffff0',
        'TRY', 0.00, 0, CURRENT_TIMESTAMP, 0.00);
