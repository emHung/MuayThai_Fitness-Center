CREATE FUNCTION publish_calendar_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  PERFORM pg_notify('muaythai_calendar', '');
  RETURN NULL;
END;
$$;

CREATE TRIGGER calendar_session_membership AFTER INSERT OR DELETE ON sessions
  FOR EACH ROW EXECUTE FUNCTION publish_calendar_change();

CREATE TRIGGER calendar_session_details AFTER UPDATE OF trainer_id,start_at,end_at,status,title ON sessions
  FOR EACH ROW EXECUTE FUNCTION publish_calendar_change();

CREATE TRIGGER calendar_registrations AFTER INSERT OR UPDATE OR DELETE ON class_signups
  FOR EACH ROW EXECUTE FUNCTION publish_calendar_change();

CREATE TRIGGER calendar_shifts AFTER INSERT OR UPDATE OR DELETE ON trainer_shifts
  FOR EACH ROW EXECUTE FUNCTION publish_calendar_change();

CREATE TRIGGER calendar_blocks AFTER INSERT OR UPDATE OR DELETE ON trainer_blocks
  FOR EACH ROW EXECUTE FUNCTION publish_calendar_change();
