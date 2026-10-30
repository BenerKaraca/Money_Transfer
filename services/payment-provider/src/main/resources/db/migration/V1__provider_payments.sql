CREATE TABLE provider_payments (
    id uuid PRIMARY KEY,
    amount numeric(19,2) NOT NULL,
    scenario varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT provider_payment_positive CHECK (amount > 0)
);
