DO $$
DECLARE
    runtime_role text := CASE current_database()
        WHEN 'auth_db' THEN 'auth_app'
        WHEN 'schedule_db' THEN 'schedule_app'
        WHEN 'checkin_db' THEN 'checkin_app'
        WHEN 'progress_db' THEN 'progress_app'
    END;
    item record;
BEGIN
    IF runtime_role IS NULL THEN
        RAISE EXCEPTION 'No application role configured for database %', current_database();
    END IF;
    FOR item IN SELECT tablename FROM pg_tables
        WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
    LOOP
        IF item.tablename IN ('account_status_events', 'password_reset_events') THEN
            EXECUTE format('GRANT SELECT, INSERT ON TABLE public.%I TO %I', item.tablename, runtime_role);
        ELSE
            EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.%I TO %I',
                item.tablename, runtime_role);
        END IF;
    END LOOP;
    EXECUTE format('GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO %I', runtime_role);
END
$$;
