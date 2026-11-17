CREATE TABLE schedule_notifications (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  session_id bigint NOT NULL REFERENCES sessions(id),
  recipient_id bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX schedule_notifications_recipient ON schedule_notifications(recipient_id, created_at DESC);
