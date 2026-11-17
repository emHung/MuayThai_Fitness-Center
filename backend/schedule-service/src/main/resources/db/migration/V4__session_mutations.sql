ALTER TABLE sessions
  ADD COLUMN mutation_id uuid,
  ADD COLUMN mutation_actor bigint,
  ADD COLUMN mutation_reason varchar(240),
  ADD COLUMN mutation_trainer bigint,
  ADD CONSTRAINT session_mutation_payload CHECK (
    (mutation_id IS NULL AND mutation_actor IS NULL AND mutation_reason IS NULL AND mutation_trainer IS NULL)
    OR (mutation_id IS NOT NULL AND mutation_actor IS NOT NULL AND mutation_reason IS NOT NULL));

CREATE INDEX sessions_pending_mutations ON sessions(id) WHERE mutation_id IS NOT NULL;
