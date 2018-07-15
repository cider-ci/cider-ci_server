
CREATE TABLE jobs ( id uuid DEFAULT uuid_generate_v4() NOT NULL,
  state character varying DEFAULT 'pending'::character varying NOT NULL,
  key text NOT NULL,
  name text NOT NULL,
  description text, result jsonb,
  tree_id character varying(40) NOT NULL,
  job_specification_id uuid NOT NULL,
  priority integer DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid,
  aborted_by uuid,
  aborted_at timestamp with time zone,
  resumed_by uuid,
  resumed_at timestamp with time zone,
  trigger_event jsonb,
  CONSTRAINT check_jobs_valid_state CHECK (((state)::text = ANY ((ARRAY['passed'::character varying, 'executing'::character varying, 'pending'::character varying, 'aborting'::character varying, 'aborted'::character varying, 'defective'::character varying, 'failed'::character varying])::text[])))
);

CREATE TRIGGER update_updated_at_column_of_jobs
  BEFORE UPDATE ON jobs
  FOR EACH ROW 
  WHEN ((old.* IS DISTINCT FROM new.*)) 
  EXECUTE PROCEDURE update_updated_at_column();

ALTER TABLE ONLY jobs ADD CONSTRAINT jobs_pkey PRIMARY KEY (id);

CREATE TRIGGER create_event_on_branches_operation
  AFTER INSERT OR UPDATE OR DELETE ON jobs
  FOR EACH ROW EXECUTE PROCEDURE create_event();

