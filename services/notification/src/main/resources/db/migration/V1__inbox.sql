CREATE TABLE inbox_events (
    event_id uuid PRIMARY KEY,
    consumed_at timestamp with time zone NOT NULL
);
CREATE TABLE notifications (
    event_id uuid PRIMARY KEY REFERENCES inbox_events(event_id),
    transfer_id uuid NOT NULL,
    message varchar(300) NOT NULL,
    created_at timestamp with time zone NOT NULL
);
