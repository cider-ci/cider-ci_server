
CREATE TABLE executors ( 
  id varchar NOT NULL,
  CONSTRAINT id_is_simple CHECK ((id ~ '^[a-z]+[a-z0-9_\-]*$')), 
  name character varying,
  description text, 
  enabled boolean DEFAULT true NOT NULL,
  traits character varying[] DEFAULT '{}'::character varying[], 
  public_key text,
  accepted_gpg_key_fingerprints character varying[] DEFAULT '{}'::character varying[], 
  version text, 
  upload_tree_attachments boolean DEFAULT true NOT NULL,
  upload_trial_attachments boolean DEFAULT true NOT NULL,
  max_load double precision DEFAULT 1.0 NOT NULL,
  CONSTRAINT max_load_is_positive CHECK ((max_load >= (0)::double precision)), 
  temporary_overload_factor double precision DEFAULT 1.5 NOT NULL,
  CONSTRAINT sensible_temoporary_overload_factor CHECK ((temporary_overload_factor >= (1.0)::double precision)),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL);


CREATE TRIGGER update_updated_at_column_of_executors
  BEFORE UPDATE ON executors
  FOR EACH ROW 
  WHEN ((old.* IS DISTINCT FROM new.*)) 
  EXECUTE PROCEDURE update_updated_at_column();

ALTER TABLE ONLY executors ADD CONSTRAINT executors_pkey PRIMARY KEY (id);

CREATE TRIGGER create_event_on_executors_operation
  AFTER INSERT OR UPDATE OR DELETE ON executors
  FOR EACH ROW EXECUTE PROCEDURE create_event();

