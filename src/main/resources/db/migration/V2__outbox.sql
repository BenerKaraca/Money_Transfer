CREATE TABLE outbox_events (
    id uuid PRIMARY KEY,
    aggregate_id uuid NOT NULL,
    event_type varchar(80) NOT NULL,
    payload varchar(2000) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone
);
CREATE INDEX outbox_unpublished_idx ON outbox_events(published_at, created_at);
