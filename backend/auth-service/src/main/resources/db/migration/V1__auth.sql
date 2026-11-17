CREATE TABLE users (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username varchar(120) NOT NULL UNIQUE,
  display_name varchar(120) NOT NULL,
  password_hash varchar(100) NOT NULL,
  role varchar(20) NOT NULL CHECK (role IN ('ADMIN', 'TRAINER', 'LEARNER')),
  locked boolean NOT NULL DEFAULT false,
  must_change_password boolean NOT NULL DEFAULT false
);

CREATE TABLE user_sessions (
  id uuid PRIMARY KEY,
  user_id bigint NOT NULL REFERENCES users(id),
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz
);

CREATE INDEX user_sessions_active_user
  ON user_sessions(user_id) WHERE revoked_at IS NULL;
