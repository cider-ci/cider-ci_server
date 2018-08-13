CREATE OR REPLACE FUNCTION jsonb_diff(val1 JSONB,val2 JSONB)
RETURNS JSONB AS $$
DECLARE
    result JSONB;
    object_result JSONB;
    i int;
    v RECORD;
BEGIN
    IF jsonb_typeof(val1) = 'null'
    THEN 
        RETURN val2;
    END IF;

    result = val1;
    FOR v IN SELECT * FROM jsonb_each(val1) LOOP
        result = result || jsonb_build_object(v.key, null);
    END LOOP;

    FOR v IN SELECT * FROM jsonb_each(val2) LOOP
        IF jsonb_typeof(val1->v.key) = 'object' AND jsonb_typeof(val2->v.key) = 'object'
        THEN
            object_result = jsonb_diff_val(val1->v.key, val2->v.key);
            -- check if result is not empty 
            i := (SELECT count(*) FROM jsonb_each(object_result));
            IF i = 0
            THEN 
                result = result - v.key; --if empty remove
            ELSE 
                result = result || jsonb_build_object(v.key,object_result);
            END IF;
        ELSIF val1->v.key = val2->v.key THEN 
            result = result - v.key;
        ELSE
            result = result || jsonb_build_object(v.key,v.value);
        END IF;
    END LOOP;
    RETURN result;
END;

$$ LANGUAGE plpgsql;
CREATE TABLE events (
    id uuid DEFAULT uuid_generate_v4() NOT NULL,
    table_name text NOT NULL,
    operation text NOT NULL,
    data_old jsonb DEFAULT '{}'::jsonb NOT NULL,
    data_new jsonb DEFAULT '{}'::jsonb NOT NULL,
    data_diff jsonb DEFAULT '{}'::jsonb NOT NULL,
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
data_diff jsonb;
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
  data_diff = jsonb_diff(data_old, data_new);
  INSERT INTO events
    (table_name, operation, data_old, data_new, data_diff)
    VALUES (TG_TABLE_NAME::text, TG_OP, data_old, data_new, data_diff);
   RETURN NEW;
END;
$$;

--CREATE TRIGGER create_event_on_branches_operation
--  AFTER INSERT OR UPDATE OR DELETE ON branches
--  FOR EACH ROW EXECUTE PROCEDURE create_event();
--
--CREATE TRIGGER create_event_on_jobs_operation
--  AFTER INSERT OR UPDATE OR DELETE ON jobs
--  FOR EACH ROW EXECUTE PROCEDURE create_event();
