CREATE TABLE password_reset_events (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES users(id),
    actor_id bigint NOT NULL REFERENCES users(id),
    reason varchar(240) NOT NULL,
    recorded_at timestamptz NOT NULL
);
