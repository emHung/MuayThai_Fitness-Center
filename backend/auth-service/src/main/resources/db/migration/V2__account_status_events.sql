CREATE TABLE account_status_events (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id bigint NOT NULL REFERENCES users(id),
  actor_id bigint NOT NULL REFERENCES users(id),
  locked boolean NOT NULL,
  reason varchar(240) NOT NULL,
  recorded_at timestamptz NOT NULL
);
