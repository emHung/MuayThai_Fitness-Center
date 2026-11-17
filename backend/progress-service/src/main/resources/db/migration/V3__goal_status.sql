ALTER TABLE monthly_goals ADD COLUMN status varchar(12);

UPDATE monthly_goals SET status=CASE
  WHEN progress=0 THEN 'NOT_STARTED'
  WHEN progress=100 THEN 'ACHIEVED'
  ELSE 'IN_PROGRESS'
END;

ALTER TABLE monthly_goals
  ALTER COLUMN status SET NOT NULL,
  ADD CONSTRAINT monthly_goals_status CHECK (status IN ('NOT_STARTED','IN_PROGRESS','ACHIEVED','NOT_ACHIEVED'));
