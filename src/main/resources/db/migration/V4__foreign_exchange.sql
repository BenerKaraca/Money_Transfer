ALTER TABLE accounts DROP CONSTRAINT accounts_currency_try;
ALTER TABLE accounts ADD CONSTRAINT accounts_currency_supported CHECK (currency IN ('TRY', 'EUR', 'USD'));

CREATE TABLE fx_trades (
    id uuid PRIMARY KEY,
    idempotency_key varchar(100) NOT NULL UNIQUE,
    quote_id uuid NOT NULL,
    source_account_id uuid NOT NULL REFERENCES accounts(id),
    destination_account_id uuid NOT NULL REFERENCES accounts(id),
    source_amount numeric(19,2) NOT NULL,
    target_amount numeric(19,2) NOT NULL,
    rate numeric(19,8) NOT NULL,
    rate_date date NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT fx_distinct_accounts CHECK (source_account_id <> destination_account_id),
    CONSTRAINT fx_positive_amounts CHECK (source_amount > 0 AND target_amount > 0)
);
CREATE INDEX fx_trades_source_created_idx ON fx_trades(source_account_id, created_at DESC);
