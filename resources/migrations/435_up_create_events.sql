CREATE TABLE events (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    table_name text NOT NULL,
    operation text NOT NULL,
    data_old jsonb DEFAULT '{}'::jsonb NOT NULL,
    data_new jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY events ADD CONSTRAINT events_pkey PRIMARY KEY (id);
CREATE INDEX index_events_on_table_name ON events USING btree (table_name);
CREATE INDEX index_events_on_operation ON events USING btree (operation);
CREATE INDEX index_events_on_created_at ON events USING btree (created_at);


CREATE OR REPLACE FUNCTION create_event() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
data_old jsonb;
data_new jsonb;
BEGIN
  CASE
    WHEN TG_OP = 'DELETE' OR TG_OP = 'TRUNCATE'  THEN
      data_old = row_to_json(OLD);
      data_new = '{}'::jsonb;
    WHEN TG_OP = 'INSERT' THEN
      data_old = '{}'::jsonb;
      data_new = row_to_json(NEW);
    ELSE
      data_old = row_to_json(OLD);
      data_new = row_to_json(NEW);
  END CASE;
   INSERT INTO events
     (table_name, operation, data_old, data_new)
      VALUES (TG_TABLE_NAME::text, TG_OP, data_old, data_new);
   RETURN NEW;
END;
$$;

CREATE TRIGGER create_event_on_branches_operation
  AFTER INSERT OR UPDATE OR DELETE ON branches
  FOR EACH ROW EXECUTE PROCEDURE create_event();

CREATE TRIGGER create_event_on_jobs_operation
  AFTER INSERT OR UPDATE OR DELETE ON jobs
  FOR EACH ROW EXECUTE PROCEDURE create_event();
