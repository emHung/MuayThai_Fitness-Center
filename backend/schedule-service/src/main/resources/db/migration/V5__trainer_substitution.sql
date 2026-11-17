ALTER TABLE sessions ADD COLUMN assigned_trainer_id bigint;

CREATE TABLE session_trainer_changes (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  session_id bigint NOT NULL REFERENCES sessions(id),
  previous_trainer_id bigint NOT NULL,
  new_trainer_id bigint NOT NULL,
  changed_by bigint NOT NULL,
  reason varchar(240) NOT NULL,
  changed_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX session_trainer_changes_previous ON session_trainer_changes(previous_trainer_id, changed_at DESC);
CREATE INDEX session_trainer_changes_new ON session_trainer_changes(new_trainer_id, changed_at DESC);
