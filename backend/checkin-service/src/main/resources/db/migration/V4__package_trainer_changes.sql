CREATE TABLE package_trainer_changes (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  package_id bigint NOT NULL REFERENCES packages(id),
  previous_trainer_id bigint,
  new_trainer_id bigint NOT NULL,
  actor_id bigint NOT NULL,
  reason varchar(240) NOT NULL,
  changed_at timestamptz NOT NULL DEFAULT now()
);
