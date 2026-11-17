DO $$
DECLARE current_constraint record;
BEGIN
  FOR current_constraint IN
    SELECT conname FROM pg_constraint
      WHERE conrelid = 'packages'::regclass AND contype = 'c'
      AND (pg_get_constraintdef(oid) ILIKE '%trainer_id%' OR pg_get_constraintdef(oid) ILIKE '%class_id%')
  LOOP
    EXECUTE format('ALTER TABLE packages DROP CONSTRAINT %I', current_constraint.conname);
  END LOOP;
END $$;

ALTER TABLE packages ADD CONSTRAINT package_target CHECK (
  (type='PT' AND trainer_id IS NOT NULL AND class_id IS NULL)
  OR (type='GROUP10' AND (trainer_id IS NOT NULL) <> (class_id IS NOT NULL))
  OR (type='GROUP30' AND trainer_id IS NULL AND class_id IS NOT NULL)
  OR (type='MEMBERSHIP' AND trainer_id IS NULL AND class_id IS NULL)
);
