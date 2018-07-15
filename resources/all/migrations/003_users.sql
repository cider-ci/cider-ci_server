--#############################################################################
-- users ######################################################################
--#############################################################################

CREATE TABLE users ( 
  id uuid DEFAULT uuid_generate_v4() NOT NULL, 
  -- github_access_token character varying,
  -- github_id integer,
  name varchar,
  searchable text,
  primary_email_address varchar NOT NULL,
  is_admin boolean DEFAULT false NOT NULL,
  password_sign_in_enabled boolean DEFAULT true NOT NULL,
  password_hash text DEFAULT public.crypt((public.gen_random_uuid())::text, public.gen_salt('bf'::text)) NOT NULL,
  sign_in_enabled boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY users ADD CONSTRAINT users_pkey PRIMARY KEY (id);
CREATE UNIQUE INDEX index_users_on_primary_email_address ON users USING btree (lower(primary_email_address));

CREATE TRIGGER update_updated_at_column_of_users
  BEFORE UPDATE ON users
  FOR EACH ROW 
  WHEN ((old.* IS DISTINCT FROM new.*)) 
  EXECUTE PROCEDURE update_updated_at_column();



CREATE FUNCTION public.users_update_searchable_column() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.searchable = COALESCE(NEW.name::text, '') || ' ' || COALESCE(NEW.primary_email_address::text, '') ;
   RETURN NEW;
END;
$$;

CREATE TRIGGER update_searchable_column_of_users 
  BEFORE INSERT OR UPDATE ON public.users 
  FOR EACH ROW EXECUTE PROCEDURE public.users_update_searchable_column();


--#############################################################################
-- email_addresses ############################################################
--#############################################################################

CREATE TABLE email_addresses ( 
  email_address character varying NOT NULL,
  user_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY email_addresses ADD CONSTRAINT email_addresses_pkey PRIMARY KEY (email_address);
CREATE INDEX index_email_addresses_on_user_id ON email_addresses USING btree (user_id);
CREATE UNIQUE INDEX index_email_addresses_on_email_address ON email_addresses USING btree (lower(email_address));


CREATE TRIGGER update_updated_at_column_of_email_addresses
  BEFORE UPDATE ON email_addresses
  FOR EACH ROW 
  WHEN ((old.* IS DISTINCT FROM new.*)) 
  EXECUTE PROCEDURE update_updated_at_column();


--#############################################################################
-- check primary_email consistency ############################################
--#############################################################################

ALTER TABLE ONLY users ADD CONSTRAINT fk_users_primary_email_address
FOREIGN KEY (primary_email_address) REFERENCES email_addresses(email_address);

CREATE FUNCTION check_primary_email_consistency() 
  RETURNS TRIGGER LANGUAGE plpgsql AS $$
  BEGIN
    If (
      (SELECT count(*) FROM users) <>
      (SELECT count(*) 
        FROM users 
        JOIN email_addresses ON users.primary_email_address = email_addresses.email_address
        WHERE email_addresses.user_id = users.id))
    THEN 
      RAISE EXCEPTION 'users.primary_email_address is inconsistent with email_addresses';
    ELSE
      RETURN NEW;
    END IF;
  END 
$$;

CREATE CONSTRAINT TRIGGER check_primary_email_consistency_on_users_change
  AFTER INSERT OR UPDATE OR DELETE ON public.users DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE PROCEDURE public.check_primary_email_consistency();

CREATE CONSTRAINT TRIGGER check_primary_email_consistency_on_email_addresses_change
  AFTER INSERT OR UPDATE OR DELETE ON public.email_addresses DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE PROCEDURE public.check_primary_email_consistency();



--#############################################################################
-- user_sessions ##############################################################
--#############################################################################

CREATE TABLE public.user_sessions ( 
  id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
  token_hash text NOT NULL,
  user_id uuid,
  created_at timestamp with time zone DEFAULT now());

ALTER TABLE ONLY public.user_sessions ADD CONSTRAINT user_sessions_pkey PRIMARY KEY (id);
CREATE UNIQUE INDEX index_user_sessions_on_token_hash ON public.user_sessions USING btree (token_hash);
CREATE INDEX index_user_sessions_on_user_id ON public.user_sessions USING btree (user_id);


ALTER TABLE ONLY public.user_sessions ADD CONSTRAINT fkey_user_sessions_users
FOREIGN KEY (user_id) REFERENCES public.users(id) ON
DELETE CASCADE;


--#############################################################################
-- api_tokens #################################################################
--#############################################################################

CREATE TABLE api_tokens ( 
  id uuid DEFAULT uuid_generate_v4() NOT NULL,
  user_id uuid NOT NULL,
  token_hash text NOT NULL,
  token_part character varying(5) NOT NULL,
  revoked boolean DEFAULT false NOT NULL,
  scope_read boolean DEFAULT true NOT NULL,
  scope_write boolean DEFAULT false NOT NULL,
  scope_admin_read boolean DEFAULT false NOT NULL,
  scope_admin_write boolean DEFAULT false NOT NULL,
  description text, created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  expires_at timestamp with time zone DEFAULT (now() + '1 year'::interval) NOT NULL);

ALTER TABLE ONLY api_tokens ADD CONSTRAINT api_tokens_pkey PRIMARY KEY (id);
CREATE UNIQUE INDEX index_api_tokens_on_token_hash ON api_tokens USING btree (token_hash);

ALTER TABLE ONLY api_tokens ADD CONSTRAINT fkey_api_tokens_user_id
  FOREIGN KEY (user_id) REFERENCES users(id) ON
  UPDATE CASCADE ON
  DELETE CASCADE;

CREATE TRIGGER update_updated_at_column_of_api_tokens
  BEFORE
  UPDATE ON api_tokens
  FOR EACH ROW 
      WHEN ((old.* IS DISTINCT FROM new.*)) 
        EXECUTE PROCEDURE update_updated_at_column();


--#############################################################################
-- gpg_pub_keys ###############################################################
--#############################################################################

CREATE TABLE gpg_keys ( 
  id uuid DEFAULT uuid_generate_v4() NOT NULL,
  user_id uuid NOT NULL,
  key text,
  description text, 
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL);

CREATE TRIGGER update_updated_at_column_of_gpg_keys
  BEFORE
  UPDATE ON gpg_keys
  FOR EACH ROW 
      WHEN ((old.* IS DISTINCT FROM new.*)) 
        EXECUTE PROCEDURE update_updated_at_column();

ALTER TABLE ONLY gpg_keys ADD CONSTRAINT gpg_keys_pkey PRIMARY KEY (id);

ALTER TABLE ONLY gpg_keys ADD CONSTRAINT fkey_gpg_keys_users
  FOREIGN KEY (user_id) REFERENCES users(id) ON
  UPDATE CASCADE ON
  DELETE CASCADE;

CREATE TABLE gpg_key_finterprints (
  id uuid DEFAULT uuid_generate_v4() NOT NULL,
  gpg_key_id uuid NOT NULL,
  fingerprint text);

ALTER TABLE ONLY gpg_key_finterprints ADD CONSTRAINT gpg_key_finterprints_pkey PRIMARY KEY (id);

ALTER TABLE ONLY gpg_key_finterprints ADD CONSTRAINT fkey_fingerprint_pub_key
  FOREIGN KEY (gpg_key_id) REFERENCES gpg_keys(id) ON
  UPDATE CASCADE ON
  DELETE CASCADE;

