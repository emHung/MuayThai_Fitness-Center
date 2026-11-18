ALTER TABLE packages DROP CONSTRAINT package_target;
ALTER TABLE packages ADD CONSTRAINT package_target CHECK (
  (type='PT' AND trainer_id IS NOT NULL AND class_id IS NULL)
  OR (type='GROUP10' AND (trainer_id IS NOT NULL OR class_id IS NOT NULL))
  OR (type='GROUP30' AND trainer_id IS NULL AND class_id IS NOT NULL)
  OR (type='MEMBERSHIP' AND trainer_id IS NULL AND class_id IS NULL)
);
