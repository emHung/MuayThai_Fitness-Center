CREATE TABLE trainer_shifts (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  trainer_id bigint NOT NULL,
  weekday int NOT NULL CHECK (weekday BETWEEN 1 AND 7),
  starts_at time NOT NULL,
  ends_at time NOT NULL CHECK (ends_at > starts_at),
  UNIQUE (trainer_id, weekday, starts_at)
);
CREATE INDEX trainer_shifts_by_day ON trainer_shifts(trainer_id,weekday);

CREATE TABLE trainer_blocks (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  trainer_id bigint NOT NULL,
  start_at timestamptz NOT NULL,
  end_at timestamptz NOT NULL CHECK (end_at > start_at),
  reason varchar(240) NOT NULL
);
CREATE INDEX trainer_blocks_by_time ON trainer_blocks(trainer_id,start_at,end_at);

CREATE TABLE pt_changes (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  session_id bigint NOT NULL REFERENCES sessions(id),
  requested_by bigint NOT NULL,
  recipient_id bigint NOT NULL,
  proposed_start timestamptz NOT NULL,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  status varchar(12) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED','EXPIRED'))
);
CREATE UNIQUE INDEX pt_changes_pending ON pt_changes(session_id) WHERE status='PENDING';
CREATE INDEX pt_changes_recipient ON pt_changes(recipient_id,status);
