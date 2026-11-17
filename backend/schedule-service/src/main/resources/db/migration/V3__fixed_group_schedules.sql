CREATE TABLE fixed_group_schedules (
  class_id bigint NOT NULL REFERENCES classes(id),
  learner_id bigint NOT NULL,
  package_id bigint NOT NULL,
  end_date date NOT NULL,
  PRIMARY KEY (class_id, learner_id)
);
