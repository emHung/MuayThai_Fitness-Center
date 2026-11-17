CREATE TABLE absence_records (
  session_id bigint NOT NULL,
  learner_id bigint NOT NULL,
  actor_id bigint NOT NULL,
  recorded_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(session_id,learner_id)
);
