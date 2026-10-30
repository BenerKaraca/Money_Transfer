CREATE TABLE app_users (
    id uuid PRIMARY KEY,
    username varchar(80) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    customer_id uuid NOT NULL REFERENCES customers(id),
    role varchar(20) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT app_user_role CHECK (role IN ('USER','ADMIN'))
);
CREATE INDEX app_users_customer_idx ON app_users(customer_id);
