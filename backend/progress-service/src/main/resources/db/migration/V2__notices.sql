CREATE TABLE broadcasts (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  title varchar(120) NOT NULL,
  message varchar(600) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  actor_id bigint NOT NULL
);
CREATE TABLE notice_reads (
  user_id bigint NOT NULL,
  notice_key varchar(160) NOT NULL,
  read_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, notice_key)
);
