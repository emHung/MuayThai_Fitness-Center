CREATE TABLE packages (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code uuid NOT NULL UNIQUE,
  learner_id bigint NOT NULL,
  learner_name varchar(120) NOT NULL,
  member_code varchar(32) NOT NULL,
  type varchar(16) NOT NULL CHECK (type IN ('PT','GROUP10','GROUP30','MEMBERSHIP')),
  trainer_id bigint,
  class_id bigint,
  start_date date NOT NULL,
  end_date date NOT NULL CHECK (end_date >= start_date),
  total_sessions int CHECK (total_sessions BETWEEN 1 AND 500),
  used_sessions int NOT NULL DEFAULT 0 CHECK (used_sessions >= 0),
  CHECK (total_sessions IS NULL OR used_sessions <= total_sessions),
  CHECK ((type='MEMBERSHIP') = (total_sessions IS NULL)),
  CHECK ((type='PT') = (trainer_id IS NOT NULL)),
  CHECK ((type IN ('GROUP10','GROUP30')) = (class_id IS NOT NULL))
);
CREATE INDEX packages_learner ON packages(learner_id);
CREATE INDEX packages_trainer ON packages(trainer_id);

CREATE TABLE checkins (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  session_id bigint,
  package_id bigint NOT NULL REFERENCES packages(id),
  learner_id bigint NOT NULL,
  trainer_id bigint,
  learner_name varchar(120) NOT NULL,
  session_title varchar(120) NOT NULL,
  service_date date NOT NULL,
  actor_id bigint NOT NULL,
  method varchar(16) NOT NULL CHECK (method IN ('QR','CODE_ENTRY','MANUAL')),
  reason varchar(240),
  checked_in_at timestamptz NOT NULL,
  remaining_after_event int,
  request_key uuid NOT NULL,
  UNIQUE (actor_id, request_key),
  UNIQUE (session_id, learner_id)
);
CREATE INDEX checkins_session ON checkins(session_id);
CREATE INDEX checkins_learner ON checkins(learner_id, checked_in_at DESC);
CREATE UNIQUE INDEX one_membership_visit_per_day ON checkins(learner_id, service_date) WHERE session_id IS NULL;

CREATE TABLE checkin_adjustments (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  checkin_id bigint NOT NULL UNIQUE REFERENCES checkins(id),
  actor_id bigint NOT NULL,
  reason varchar(240) NOT NULL,
  adjusted_at timestamptz NOT NULL,
  remaining_before int,
  remaining_after int,
  request_key uuid NOT NULL,
  UNIQUE(actor_id, request_key)
);
