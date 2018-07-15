CREATE TABLE settings (
  id int DEFAULT 0 NOT NULL,
  accept_server_secret_as_universal_password boolean DEFAULT true NOT NULL,
  sessions_force_secure boolean DEFAULT false NOT NULL,
  sessions_force_uniqueness boolean DEFAULT true NOT NULL,
  sessions_max_lifetime_secs integer DEFAULT 432000,
  
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  CONSTRAINT the_one_and_only CHECK (id = 0)
);

ALTER TABLE ONLY settings ADD CONSTRAINT settings_pkey PRIMARY KEY (id);

CREATE TRIGGER update_updated_at_column_of_settings
BEFORE UPDATE ON settings FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*))
EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER create_event_on_settings_operation
AFTER INSERT OR UPDATE OR DELETE ON settings
FOR EACH ROW EXECUTE PROCEDURE create_event();

INSERT INTO settings (id) VALUES (0);
