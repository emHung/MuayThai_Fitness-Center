CREATE SEQUENCE learner_code_seq;
ALTER TABLE users ADD COLUMN member_code varchar(32) UNIQUE;
ALTER TABLE users ADD COLUMN phone varchar(32);
ALTER TABLE users ADD COLUMN specialty varchar(200);
ALTER TABLE users ADD COLUMN can_adjust_checkins boolean NOT NULL DEFAULT false;
UPDATE users SET member_code = 'HV' || nextval('learner_code_seq') WHERE role = 'LEARNER';
UPDATE users SET can_adjust_checkins = true WHERE role = 'ADMIN' AND username = 'admin@example.test';

CREATE FUNCTION assign_learner_code() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.role = 'LEARNER' AND NEW.member_code IS NULL THEN
    NEW.member_code := 'HV' || nextval('learner_code_seq');
  END IF;
  RETURN NEW;
END
$$;

CREATE TRIGGER learner_code BEFORE INSERT ON users
FOR EACH ROW EXECUTE FUNCTION assign_learner_code();

ALTER TABLE users ADD CONSTRAINT learner_requires_code
  CHECK ((role = 'LEARNER') = (member_code IS NOT NULL));
