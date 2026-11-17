CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE classes (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  title varchar(120) NOT NULL,
  trainer_id bigint NOT NULL,
  type varchar(12) NOT NULL CHECK (type IN ('GROUP30', 'GROUP10')),
  status varchar(12) NOT NULL CHECK (status IN ('PENDING', 'OPEN', 'STOPPED', 'REJECTED')),
  weekdays integer[] NOT NULL,
  starts_at time NOT NULL,
  ends_at time NOT NULL CHECK (ends_at > starts_at)
);

CREATE TABLE sessions (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  type varchar(12) NOT NULL CHECK (type IN ('PT', 'GROUP30', 'GROUP10')),
  title varchar(120) NOT NULL,
  trainer_id bigint NOT NULL,
  learner_id bigint,
  class_id bigint REFERENCES classes(id),
  package_id bigint,
  start_at timestamptz NOT NULL,
  end_at timestamptz NOT NULL CHECK (end_at > start_at),
  capacity int NOT NULL CHECK (capacity IN (1,10,30)),
  status varchar(20) NOT NULL CHECK (status IN ('PENDING_TRAINER','CONFIRMED','CANCELLED','REJECTED')),
  reason varchar(240),
  UNIQUE (class_id, start_at),
  CHECK ((type = 'PT') = (learner_id IS NOT NULL AND package_id IS NOT NULL)),
  CONSTRAINT trainer_overlap EXCLUDE USING gist
    (trainer_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&)
    WHERE (status IN ('PENDING_TRAINER','CONFIRMED')),
  CONSTRAINT learner_overlap EXCLUDE USING gist
    (learner_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&)
    WHERE (learner_id IS NOT NULL AND status IN ('PENDING_TRAINER','CONFIRMED'))
);

CREATE INDEX sessions_by_time ON sessions(start_at);
CREATE INDEX sessions_by_class ON sessions(class_id, start_at);
CREATE INDEX sessions_by_trainer ON sessions(trainer_id, start_at);
CREATE INDEX sessions_by_learner ON sessions(learner_id, start_at);

CREATE TABLE class_signups (
  session_id bigint NOT NULL REFERENCES sessions(id),
  learner_id bigint NOT NULL,
  package_id bigint NOT NULL,
  cancelled boolean NOT NULL DEFAULT false,
  PRIMARY KEY (session_id, learner_id)
);
