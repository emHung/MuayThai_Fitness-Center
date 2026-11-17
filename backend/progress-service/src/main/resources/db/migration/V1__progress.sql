CREATE TABLE session_notes (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  session_id bigint NOT NULL UNIQUE,
  learner_id bigint,
  trainer_id bigint NOT NULL,
  trainer_name varchar(120) NOT NULL,
  session_title varchar(120) NOT NULL,
  session_at timestamptz NOT NULL,
  content varchar(3000) NOT NULL,
  comment varchar(3000),
  rating int CHECK (rating BETWEEN 1 AND 5),
  updated_at timestamptz NOT NULL
);
CREATE INDEX session_notes_learner ON session_notes(learner_id, session_at DESC);

CREATE TABLE monthly_goals (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  learner_id bigint NOT NULL,
  trainer_id bigint NOT NULL,
  target_month date NOT NULL CHECK (extract(day FROM target_month) = 1),
  title varchar(160) NOT NULL,
  description varchar(2000) NOT NULL,
  progress int NOT NULL CHECK (progress BETWEEN 0 AND 100),
  updated_at timestamptz NOT NULL,
  UNIQUE (learner_id, trainer_id, target_month)
);
