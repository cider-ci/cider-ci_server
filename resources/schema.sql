-- ----------------------------------------------------------------------------
--  Schema --------------------------------------------------------------------
-- ----------------------------------------------------------------------------
--
-- PostgreSQL database dump
--

-- Dumped from database version 10.19
-- Dumped by pg_dump version 10.19

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';


--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


--
-- Name: add_fast_forward_ancestors_to_branches_commits(uuid, character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.add_fast_forward_ancestors_to_branches_commits(branch_id uuid, commit_id character varying) RETURNS void
    LANGUAGE sql
    AS $$
      INSERT INTO branches_commits (branch_id,commit_id)
        SELECT * FROM fast_forward_ancestors_to_be_added_to_branches_commits(branch_id,commit_id)
      $$;


--
-- Name: array_filter_regex_vals(text[]); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.array_filter_regex_vals(text[]) RETURNS text[]
    LANGUAGE plpgsql
    AS $_$
DECLARE
  s text;
  rest text[];
  filtered_rest text[];
  n int;
BEGIN
  n = array_length($1, 1);
  IF n >= 1 THEN
    s = $1[1];
    rest = $1[2:n];
    filtered_rest = array_filter_regex_vals(rest);
    IF s LIKE '^%$' THEN
      RETURN array_prepend(s, filtered_rest);
    ELSE
      RETURN filtered_rest;
    END IF;
  ELSE
    RETURN $1;
  END IF;
END;
$_$;


--
-- Name: clean_branch_update_events(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.clean_branch_update_events() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  DELETE FROM branch_update_events
    WHERE created_at < NOW() - INTERVAL '3 days';
  RETURN NULL;
END;
$$;


--
-- Name: clean_executor_events(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.clean_executor_events() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  DELETE FROM executor_events
    WHERE created_at < NOW() - INTERVAL '3 days';
  RETURN NULL;
END;
$$;


--
-- Name: clean_job_state_update_events(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.clean_job_state_update_events() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  DELETE FROM job_state_update_events
    WHERE created_at < NOW() - INTERVAL '3 days';
  RETURN NULL;
END;
$$;


--
-- Name: clean_repository_events(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.clean_repository_events() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  DELETE FROM repository_events
    WHERE created_at < NOW() - INTERVAL '3 days';
  RETURN NULL;
END;
$$;


--
-- Name: clean_script_state_update_events(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.clean_script_state_update_events() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  DELETE FROM script_state_update_events
    WHERE created_at < NOW() - INTERVAL '3 days';
  RETURN NULL;
END;
$$;


--
-- Name: clean_task_state_update_events(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.clean_task_state_update_events() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  DELETE FROM task_state_update_events
    WHERE created_at < NOW() - INTERVAL '3 days';
  RETURN NULL;
END;
$$;


--
-- Name: clean_trial_state_update_events(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.clean_trial_state_update_events() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  DELETE FROM trial_state_update_events
    WHERE created_at < NOW() - INTERVAL '3 days';
  RETURN NULL;
END;
$$;


--
-- Name: clean_user_events(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.clean_user_events() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  DELETE FROM user_events
    WHERE created_at < NOW() - INTERVAL '3 days';
  RETURN NULL;
END;
$$;


--
-- Name: create_branch_update_event(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_branch_update_event() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
tree_id TEXT;
BEGIN
   SELECT commits.tree_id INTO tree_id
      FROM commits
      WHERE id = NEW.current_commit_id;
   INSERT INTO branch_update_events
    (tree_id, branch_id)
    VALUES (tree_id, NEW.id);
   RETURN NEW;
END;
$$;


--
-- Name: create_event(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_event() RETURNS trigger
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


--
-- Name: create_job_state_update_events(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_job_state_update_events() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   INSERT INTO job_state_update_events
    (job_id, state) VALUES (New.id, NEW.state);
   RETURN NEW;
END;
$$;


--
-- Name: create_pending_create_trials_evaluations_on_tasks_insert(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_pending_create_trials_evaluations_on_tasks_insert() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   INSERT INTO pending_create_trials_evaluations
    (task_id) VALUES (NEW.id);
   RETURN NEW;
END;
$$;


--
-- Name: create_pending_create_trials_evaluations_on_trial_state_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_pending_create_trials_evaluations_on_trial_state_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  task_id UUID;
BEGIN
   SELECT trials.task_id INTO task_id
      FROM trials
      WHERE trials.id = NEW.trial_id;
   INSERT INTO pending_create_trials_evaluations
    (task_id, trial_state_update_event_id) VALUES (task_id, NEW.id);
   RETURN NEW;
END;
$$;


--
-- Name: create_pending_job_evaluation_on_task_state_update_event_insert(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_pending_job_evaluation_on_task_state_update_event_insert() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  t_id UUID;
BEGIN
   SELECT job_id INTO t_id
      FROM tasks
      WHERE tasks.id = NEW.task_id;
   INSERT INTO pending_job_evaluations
    (job_id, task_state_update_event_id) VALUES (t_id, NEW.id);
   RETURN NEW;
END;
$$;


--
-- Name: create_pending_result_propagation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_pending_result_propagation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   INSERT INTO pending_result_propagations
    (trial_id) VALUES (NEW.id);
   RETURN NEW;
END;
$$;


--
-- Name: create_pending_task_evaluation_on_trial_state_update_event_inse(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_pending_task_evaluation_on_trial_state_update_event_inse() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  t_id UUID;
BEGIN
   SELECT task_id INTO t_id
      FROM trials
      WHERE trials.id = NEW.trial_id;
   INSERT INTO pending_task_evaluations
    (task_id, trial_state_update_event_id) VALUES (t_id, NEW.id);
   RETURN NEW;
END;
$$;


--
-- Name: create_script_state_update_events(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_script_state_update_events() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   INSERT INTO script_state_update_events
    (script_id, state) VALUES (New.id, NEW.state);
   RETURN NEW;
END;
$$;


--
-- Name: create_task_state_update_events(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_task_state_update_events() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   INSERT INTO task_state_update_events
    (task_id, state) VALUES (New.id, NEW.state);
   RETURN NEW;
END;
$$;


--
-- Name: create_tree_id_notification_on_branch_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_tree_id_notification_on_branch_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
tree_id TEXT;
BEGIN
   SELECT commits.tree_id INTO tree_id
      FROM commits
      WHERE id = NEW.current_commit_id;
   INSERT INTO tree_id_notifications
    (tree_id, branch_id,description)
    VALUES (tree_id, NEW.id,TG_OP);
   RETURN NEW;
END;
$$;


--
-- Name: create_tree_id_notification_on_job_state_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_tree_id_notification_on_job_state_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  INSERT INTO tree_id_notifications
    (tree_id, job_id,description)
    VALUES (NEW.tree_id, NEW.id, NEW.state);

  INSERT INTO tree_id_notifications
    (tree_id, job_id, description)
  SELECT DISTINCT
    supermodule_commits.tree_id, NEW.id, NEW.state
  FROM commits AS submodule_commits
  INNER JOIN submodules
    ON submodule_commit_id = submodule_commits.id
  INNER JOIN commits AS supermodule_commits
    ON submodules.commit_id = supermodule_commits.id
  WHERE submodule_commits.tree_id = NEW.tree_id;

  RETURN NEW;
END;
$$;


--
-- Name: create_trial_state_update_events(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_trial_state_update_events() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   INSERT INTO trial_state_update_events
    (trial_id, state) VALUES (New.id, NEW.state);
   RETURN NEW;
END;
$$;


--
-- Name: executor_event(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.executor_event() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  CASE
    WHEN TG_OP = 'DELETE' THEN
      INSERT INTO executor_events
        (executor_id, event) VALUES (OLD.id, TG_OP);
    WHEN TG_OP = 'TRUNCATE' THEN
      INSERT INTO executor_events (event) VALUES (TG_OP);
    ELSE
      INSERT INTO executor_events
        (executor_id, event) VALUES (NEW.id, TG_OP);
  END CASE;
  RETURN NULL;
END;
$$;


--
-- Name: fast_forward_ancestors_to_be_added_to_branches_commits(uuid, character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fast_forward_ancestors_to_be_added_to_branches_commits(branch_id uuid, commit_id character varying) RETURNS TABLE(branch_id uuid, commit_id character varying)
    LANGUAGE sql
    AS $_$
        WITH RECURSIVE arcs(parent_id,child_id) AS
          (SELECT $2::varchar, NULL::varchar
            UNION
           SELECT commit_arcs.* FROM commit_arcs, arcs
            WHERE arcs.parent_id = commit_arcs.child_id
            AND NOT EXISTS (SELECT 1 FROM branches_commits WHERE commit_id = arcs.parent_id AND branch_id = $1)
          )
        SELECT DISTINCT $1, parent_id FROM arcs
        WHERE NOT EXISTS (SELECT * FROM branches_commits WHERE commit_id = parent_id AND branch_id = $1)
      $_$;


--
-- Name: is_ancestor(character varying, character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.is_ancestor(node character varying, possible_ancestor character varying) RETURNS boolean
    LANGUAGE sql
    AS $_$
        SELECT ( EXISTS (SELECT * FROM with_ancestors(node) WHERE ancestor_id = possible_ancestor)
                  AND $1 <> $2 )
      $_$;


--
-- Name: is_descendant(character varying, character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.is_descendant(node character varying, possible_descendant character varying) RETURNS boolean
    LANGUAGE sql
    AS $_$
        SELECT ( EXISTS (SELECT * FROM with_descendants(node) WHERE descendant_id = possible_descendant)
                  AND $1 <> $2 )
      $_$;


--
-- Name: repository_event(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.repository_event() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  CASE
    WHEN TG_OP = 'DELETE' THEN
      INSERT INTO repository_events
        (repository_id, event) VALUES (OLD.id, TG_OP);
    WHEN TG_OP = 'TRUNCATE' THEN
      INSERT INTO repository_events (event) VALUES (TG_OP);
    ELSE
      INSERT INTO repository_events
        (repository_id, event) VALUES (NEW.id, TG_OP);
  END CASE;
  RETURN NULL;
END;
$$;


--
-- Name: update_branches_commits(uuid, character varying, character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_branches_commits(branch_id uuid, new_commit_id character varying, old_commit_id character varying) RETURNS character varying
    LANGUAGE plpgsql
    AS $_$
      BEGIN
        CASE
        WHEN (branch_id IS NULL) THEN
          RAISE 'branch_id may not be null';
        WHEN NOT EXISTS (SELECT * FROM branches WHERE id = branch_id) THEN
          RAISE 'branch_id must refer to an existing branch';
        WHEN new_commit_id IS NULL THEN
          RAISE 'new_commit_id may not be null';
        WHEN NOT EXISTS (SELECT * FROM commits WHERE id = new_commit_id) THEN
          RAISE 'new_commit_id must refer to an existing commit';
        WHEN old_commit_id IS NULL THEN
          -- entirely new branch (nothing should be in branches_commits)
          -- or request a complete reset by setting old_commit_id to NULL
          DELETE FROM branches_commits WHERE branches_commits.branch_id = $1;
        WHEN NOT is_ancestor(new_commit_id,old_commit_id) THEN
          -- this is the hard non fast forward case
          -- remove all ancestors of old_commit_id which are not ancestors of new_commit_id
          DELETE FROM branches_commits
            WHERE branches_commits.branch_id = $1
            AND branches_commits.commit_id IN ( SELECT * FROM with_ancestors(old_commit_id)
                                EXCEPT SELECT * from with_ancestors(new_commit_id) );
        ELSE
          -- this is the fast forward case; see last statement
        END CASE;
        -- whats left is adding as if we are in the fast forward case
        PERFORM add_fast_forward_ancestors_to_branches_commits(branch_id,new_commit_id);
        RETURN 'done';
      END;
      $_$;


--
-- Name: update_updated_at_column(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_updated_at_column() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: user_event(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.user_event() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  CASE
    WHEN TG_OP = 'DELETE' THEN
      INSERT INTO user_events
        (user_id, event) VALUES (OLD.id, TG_OP);
    WHEN TG_OP = 'TRUNCATE' THEN
      INSERT INTO user_events (event) VALUES (TG_OP);
    ELSE
      INSERT INTO user_events
        (user_id, event) VALUES (NEW.id, TG_OP);
  END CASE;
  RETURN NULL;
END;
$$;


--
-- Name: with_ancestors(character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.with_ancestors(character varying) RETURNS TABLE(ancestor_id character varying)
    LANGUAGE sql
    AS $_$
      WITH RECURSIVE arcs(parent_id,child_id) AS
        (SELECT $1::varchar, NULL::varchar
          UNION
         SELECT commit_arcs.* FROM commit_arcs, arcs WHERE arcs.parent_id = commit_arcs.child_id
        )
      SELECT parent_id FROM arcs
      $_$;


--
-- Name: with_descendants(character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.with_descendants(character varying) RETURNS TABLE(descendant_id character varying)
    LANGUAGE sql
    AS $_$
      WITH RECURSIVE arcs(parent_id,child_id) AS
        (SELECT NULL::varchar, $1::varchar
          UNION
         SELECT commit_arcs.* FROM commit_arcs, arcs WHERE arcs.child_id = commit_arcs.parent_id
        )
      SELECT child_id FROM arcs
      $_$;


SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: api_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.api_tokens (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    token_hash character varying(45) NOT NULL,
    token_part character varying(5) NOT NULL,
    revoked boolean DEFAULT false NOT NULL,
    scope_read boolean DEFAULT true NOT NULL,
    scope_write boolean DEFAULT false NOT NULL,
    scope_admin_read boolean DEFAULT false NOT NULL,
    scope_admin_write boolean DEFAULT false NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone DEFAULT (now() + '1 year'::interval) NOT NULL,
    CONSTRAINT sensible_scope_admin_read CHECK (((NOT scope_admin_read) OR (scope_admin_read AND scope_write AND scope_read))),
    CONSTRAINT sensible_scrope_admin_write CHECK (((NOT scope_admin_write) OR (scope_admin_write AND scope_admin_read))),
    CONSTRAINT sensible_scrope_write CHECK (((NOT scope_write) OR (scope_write AND scope_read)))
);


--
-- Name: branch_update_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.branch_update_events (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    branch_id uuid NOT NULL,
    tree_id character varying(40) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: branches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.branches (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    repository_id uuid NOT NULL,
    name character varying NOT NULL,
    current_commit_id character varying(40) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: branches_commits; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.branches_commits (
    branch_id uuid NOT NULL,
    commit_id character varying(40) NOT NULL
);


--
-- Name: commit_arcs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.commit_arcs (
    parent_id character varying(40) NOT NULL,
    child_id character varying(40) NOT NULL
);


--
-- Name: commits; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.commits (
    id character varying(40) NOT NULL,
    tree_id character varying(40),
    depth integer,
    author_name character varying,
    author_email character varying,
    author_date timestamp with time zone,
    committer_name character varying,
    committer_email character varying,
    committer_date timestamp with time zone,
    subject text,
    body text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.jobs (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    state character varying DEFAULT 'pending'::character varying NOT NULL,
    key text NOT NULL,
    name text NOT NULL,
    description text,
    result jsonb,
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
    CONSTRAINT check_jobs_valid_state CHECK (((state)::text = ANY (ARRAY[('passed'::character varying)::text, ('executing'::character varying)::text, ('pending'::character varying)::text, ('aborting'::character varying)::text, ('aborted'::character varying)::text, ('defective'::character varying)::text, ('failed'::character varying)::text])))
);


--
-- Name: repositories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.repositories (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    git_url text NOT NULL,
    name character varying,
    public_view_permission boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    update_notification_token uuid DEFAULT public.uuid_generate_v4(),
    proxy_id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    branch_trigger_include_match text DEFAULT '^.*$'::text NOT NULL,
    branch_trigger_exclude_match text DEFAULT ''::text NOT NULL,
    remote_api_endpoint character varying,
    remote_api_token character varying,
    remote_api_namespace character varying,
    remote_api_name character varying,
    remote_api_type text,
    remote_fetch_interval text DEFAULT '1 Minute'::text NOT NULL,
    remote_api_token_bearer character varying,
    send_status_notifications boolean DEFAULT true NOT NULL,
    manage_remote_push_hooks boolean DEFAULT false NOT NULL,
    branch_trigger_max_commit_age text DEFAULT '12 hours'::text,
    cron_trigger_enabled boolean DEFAULT false,
    all_executors_permitted boolean DEFAULT true NOT NULL,
    all_users_permitted boolean DEFAULT true NOT NULL,
    CONSTRAINT branch_trigger_max_commit_age_not_blank CHECK ((branch_trigger_max_commit_age !~ '^\s*$'::text)),
    CONSTRAINT check_valid_remote_api_type CHECK ((remote_api_type = ANY (ARRAY['github'::text, 'gitlab'::text, 'bitbucket'::text]))),
    CONSTRAINT foreign_api_authtoken_not_empty CHECK (((remote_api_token)::text <> ''::text)),
    CONSTRAINT foreign_api_endpoint_not_empty CHECK (((remote_api_endpoint)::text <> ''::text)),
    CONSTRAINT foreign_api_owner_not_empty CHECK (((remote_api_namespace)::text <> ''::text)),
    CONSTRAINT foreign_api_repo_not_empty CHECK (((remote_api_name)::text <> ''::text)),
    CONSTRAINT foreign_api_token_bearer_not_empty CHECK (((remote_api_token_bearer)::text <> ''::text))
);


--
-- Name: tree_issues; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tree_issues (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    title text,
    description text,
    type character varying DEFAULT 'error'::character varying NOT NULL,
    tree_id text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: commit_cache_signatures; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.commit_cache_signatures AS
 SELECT commits.id AS commit_id,
    (count(tree_issues.*) > 0) AS has_tree_issues,
    md5(string_agg(DISTINCT (branches.updated_at)::text, ',
            '::text ORDER BY (branches.updated_at)::text)) AS branches_signature,
    md5(string_agg(DISTINCT (repositories.updated_at)::text, ',
            '::text ORDER BY (repositories.updated_at)::text)) AS repositories_signature,
    md5(string_agg(DISTINCT (jobs.updated_at)::text, ',
            '::text ORDER BY (jobs.updated_at)::text)) AS jobs_signature
   FROM (((((public.commits
     LEFT JOIN public.branches_commits ON (((branches_commits.commit_id)::text = (commits.id)::text)))
     LEFT JOIN public.branches ON ((branches_commits.branch_id = branches.id)))
     LEFT JOIN public.jobs ON (((jobs.tree_id)::text = (commits.tree_id)::text)))
     LEFT JOIN public.repositories ON ((branches.repository_id = repositories.id)))
     LEFT JOIN public.tree_issues ON ((tree_issues.tree_id = (commits.tree_id)::text)))
  GROUP BY commits.id;


--
-- Name: email_addresses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_addresses (
    user_id uuid,
    email_address character varying NOT NULL,
    "primary" boolean DEFAULT false NOT NULL
);


--
-- Name: events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.events (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    table_name text NOT NULL,
    operation text NOT NULL,
    data_old jsonb DEFAULT '{}'::jsonb NOT NULL,
    data_new jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: executor_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.executor_events (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    executor_id uuid,
    event text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: executor_issues; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.executor_issues (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    title text,
    description text,
    type character varying DEFAULT 'error'::character varying NOT NULL,
    executor_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: executors; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.executors (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    name character varying NOT NULL,
    max_load double precision DEFAULT 1.0 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    traits character varying[] DEFAULT '{}'::character varying[],
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    accepted_repositories character varying[] DEFAULT '{}'::character varying[],
    upload_tree_attachments boolean DEFAULT true NOT NULL,
    upload_trial_attachments boolean DEFAULT true NOT NULL,
    version text,
    temporary_overload_factor double precision DEFAULT 1.5 NOT NULL,
    token_hash text,
    token_part text,
    description text,
    CONSTRAINT max_load_is_positive CHECK ((max_load >= (0)::double precision)),
    CONSTRAINT sensible_temoporary_overload_factor CHECK ((temporary_overload_factor >= (1.0)::double precision))
);


--
-- Name: tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tasks (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    job_id uuid NOT NULL,
    state character varying DEFAULT 'pending'::character varying NOT NULL,
    name text NOT NULL,
    result jsonb,
    task_specification_id uuid NOT NULL,
    priority integer DEFAULT 0 NOT NULL,
    traits character varying[] DEFAULT '{}'::character varying[] NOT NULL,
    exclusive_global_resources character varying[] DEFAULT '{}'::character varying[] NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    entity_errors jsonb DEFAULT '[]'::jsonb,
    dispatch_storm_delay_seconds integer DEFAULT 1 NOT NULL,
    load double precision DEFAULT 1.0 NOT NULL,
    CONSTRAINT check_tasks_valid_state CHECK (((state)::text = ANY (ARRAY[('aborted'::character varying)::text, ('aborting'::character varying)::text, ('defective'::character varying)::text, ('executing'::character varying)::text, ('failed'::character varying)::text, ('passed'::character varying)::text, ('pending'::character varying)::text]))),
    CONSTRAINT load_is_stricly_positive CHECK ((load > (0)::double precision))
);


--
-- Name: trials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trials (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    task_id uuid NOT NULL,
    executor_id uuid,
    error text,
    state character varying DEFAULT 'pending'::character varying NOT NULL,
    result jsonb,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    aborted_by uuid,
    aborted_at timestamp with time zone,
    token uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    dispatched_at timestamp with time zone,
    CONSTRAINT check_trials_valid_state CHECK (((state)::text = ANY (ARRAY[('aborted'::character varying)::text, ('aborting'::character varying)::text, ('defective'::character varying)::text, ('dispatching'::character varying)::text, ('executing'::character varying)::text, ('failed'::character varying)::text, ('passed'::character varying)::text, ('pending'::character varying)::text])))
);


--
-- Name: executors_load; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.executors_load AS
 SELECT count(trials.id) AS trials_count,
    sum(COALESCE(tasks.load, (0.0)::double precision)) AS current_load,
    executors.id AS executor_id
   FROM ((public.executors
     LEFT JOIN public.trials ON (((trials.executor_id = executors.id) AND ((trials.state)::text = ANY (ARRAY[('aborting'::character varying)::text, ('dispatching'::character varying)::text, ('executing'::character varying)::text])))))
     LEFT JOIN public.tasks ON ((tasks.id = trials.task_id)))
  GROUP BY executors.id;


--
-- Name: executors_with_load; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.executors_with_load AS
 SELECT executors.id,
    executors.name,
    executors.max_load,
    executors.enabled,
    executors.traits,
    executors.created_at,
    executors.updated_at,
    executors.accepted_repositories,
    executors.upload_tree_attachments,
    executors.upload_trial_attachments,
    executors.version,
    executors.temporary_overload_factor,
    executors.token_hash,
    executors.token_part,
    executors.description,
    executors_load.trials_count,
    executors_load.current_load,
    executors_load.executor_id,
    (executors_load.current_load / executors.max_load) AS relative_load
   FROM (public.executors
     JOIN public.executors_load ON ((executors_load.executor_id = executors.id)));


--
-- Name: job_cache_signatures; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.job_cache_signatures AS
SELECT
    NULL::uuid AS job_id,
    NULL::text AS branches_signature,
    NULL::text AS commits_signature,
    NULL::text AS job_issues_signature,
    NULL::bigint AS job_issues_count,
    NULL::text AS repositories_signature,
    NULL::text AS tasks_signature,
    NULL::bigint AS tree_attachments_count;


--
-- Name: job_issues; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.job_issues (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    title text,
    description text,
    type character varying DEFAULT 'error'::character varying NOT NULL,
    job_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: job_specifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.job_specifications (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    data jsonb
);


--
-- Name: job_state_update_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.job_state_update_events (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    job_id uuid,
    state character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT check_valid_state CHECK (((state)::text = ANY (ARRAY[('passed'::character varying)::text, ('executing'::character varying)::text, ('pending'::character varying)::text, ('aborting'::character varying)::text, ('aborted'::character varying)::text, ('defective'::character varying)::text, ('failed'::character varying)::text])))
);


--
-- Name: job_stats; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.job_stats AS
 SELECT jobs.id AS job_id,
    ( SELECT count(*) AS count
           FROM public.tasks
          WHERE (tasks.job_id = jobs.id)) AS total,
    ( SELECT count(*) AS count
           FROM public.tasks
          WHERE ((tasks.job_id = jobs.id) AND ((tasks.state)::text = 'passed'::text))) AS passed,
    ( SELECT count(*) AS count
           FROM public.tasks
          WHERE ((tasks.job_id = jobs.id) AND ((tasks.state)::text = 'executing'::text))) AS executing,
    ( SELECT count(*) AS count
           FROM public.tasks
          WHERE ((tasks.job_id = jobs.id) AND ((tasks.state)::text = 'pending'::text))) AS pending,
    ( SELECT count(*) AS count
           FROM public.tasks
          WHERE ((tasks.job_id = jobs.id) AND ((tasks.state)::text = 'aborting'::text))) AS aborting,
    ( SELECT count(*) AS count
           FROM public.tasks
          WHERE ((tasks.job_id = jobs.id) AND ((tasks.state)::text = 'aborted'::text))) AS aborted,
    ( SELECT count(*) AS count
           FROM public.tasks
          WHERE ((tasks.job_id = jobs.id) AND ((tasks.state)::text = 'defective'::text))) AS defective,
    ( SELECT count(*) AS count
           FROM public.tasks
          WHERE ((tasks.job_id = jobs.id) AND ((tasks.state)::text = 'failed'::text))) AS failed
   FROM public.jobs;


--
-- Name: pending_create_trials_evaluations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pending_create_trials_evaluations (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    task_id uuid NOT NULL,
    trial_state_update_event_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: pending_job_evaluations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pending_job_evaluations (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    job_id uuid NOT NULL,
    task_state_update_event_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: pending_result_propagations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pending_result_propagations (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    trial_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: pending_task_evaluations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pending_task_evaluations (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    task_id uuid NOT NULL,
    trial_state_update_event_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: repository_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.repository_events (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    repository_id uuid,
    event text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: repository_executor_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.repository_executor_permissions (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    executor_id uuid NOT NULL,
    repository_id uuid NOT NULL,
    execute boolean DEFAULT true NOT NULL
);


--
-- Name: repository_user_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.repository_user_permissions (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    repository_id uuid NOT NULL,
    execute boolean DEFAULT true NOT NULL,
    manage boolean DEFAULT false NOT NULL
);


--
-- Name: schema_migrations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.schema_migrations (
    version character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: script_state_update_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.script_state_update_events (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    script_id uuid,
    state character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT check_valid_state CHECK (((state)::text = ANY (ARRAY[('aborted'::character varying)::text, ('defective'::character varying)::text, ('executing'::character varying)::text, ('failed'::character varying)::text, ('passed'::character varying)::text, ('pending'::character varying)::text, ('skipped'::character varying)::text, ('waiting'::character varying)::text])))
);


--
-- Name: scripts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.scripts (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    trial_id uuid NOT NULL,
    key character varying NOT NULL,
    state character varying DEFAULT 'pending'::character varying NOT NULL,
    name character varying NOT NULL,
    stdout character varying(10485760) DEFAULT ''::character varying NOT NULL,
    stderr character varying(10485760) DEFAULT ''::character varying NOT NULL,
    body character varying(10240) DEFAULT ''::character varying NOT NULL,
    timeout character varying,
    exclusive_executor_resource character varying,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    start_when jsonb DEFAULT '[]'::jsonb NOT NULL,
    terminate_when jsonb DEFAULT '[]'::jsonb NOT NULL,
    environment_variables jsonb DEFAULT '{}'::jsonb NOT NULL,
    ignore_abort boolean DEFAULT false NOT NULL,
    ignore_state boolean DEFAULT false NOT NULL,
    template_environment_variables boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    exit_status integer,
    command jsonb,
    working_dir character varying(2048),
    script_file character varying(2048),
    wrapper_file character varying(2048),
    issues jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT check_scripts_valid_state CHECK (((state)::text = ANY (ARRAY[('aborted'::character varying)::text, ('defective'::character varying)::text, ('executing'::character varying)::text, ('failed'::character varying)::text, ('passed'::character varying)::text, ('pending'::character varying)::text, ('skipped'::character varying)::text, ('waiting'::character varying)::text])))
);


--
-- Name: submodules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.submodules (
    submodule_commit_id character varying(40) NOT NULL,
    path text NOT NULL,
    commit_id character varying(40) NOT NULL
);


--
-- Name: task_specifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task_specifications (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    data jsonb
);


--
-- Name: task_state_update_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task_state_update_events (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    task_id uuid,
    state character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT check_valid_state CHECK (((state)::text = ANY (ARRAY[('aborted'::character varying)::text, ('aborting'::character varying)::text, ('defective'::character varying)::text, ('executing'::character varying)::text, ('failed'::character varying)::text, ('passed'::character varying)::text, ('pending'::character varying)::text])))
);


--
-- Name: tree_attachments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tree_attachments (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    path text NOT NULL,
    content_length text,
    content_type text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tree_id text NOT NULL,
    CONSTRAINT check_tree_id CHECK ((length(tree_id) = 40))
);


--
-- Name: tree_id_notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tree_id_notifications (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    tree_id character varying(40) NOT NULL,
    branch_id uuid,
    job_id uuid,
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: trial_attachments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trial_attachments (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    path text NOT NULL,
    content_length text,
    content_type text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    trial_id uuid NOT NULL
);


--
-- Name: trial_issues; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trial_issues (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    title text,
    description text,
    type character varying DEFAULT 'error'::character varying NOT NULL,
    trial_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: trial_state_update_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trial_state_update_events (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    trial_id uuid,
    state character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT check_valid_state CHECK (((state)::text = ANY (ARRAY[('aborted'::character varying)::text, ('aborting'::character varying)::text, ('defective'::character varying)::text, ('dispatching'::character varying)::text, ('executing'::character varying)::text, ('failed'::character varying)::text, ('passed'::character varying)::text, ('pending'::character varying)::text])))
);


--
-- Name: user_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_events (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    user_id uuid,
    event text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    password_digest character varying,
    login character varying NOT NULL,
    is_admin boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    workspace_filters jsonb,
    mini_profiler_is_enabled boolean DEFAULT false,
    reload_frequency character varying,
    ui_theme character varying,
    name character varying,
    github_access_token character varying,
    github_id integer,
    account_enabled boolean DEFAULT true NOT NULL,
    password_sign_in_allowed boolean DEFAULT true NOT NULL,
    max_session_lifetime character varying DEFAULT '7 days'::character varying
);


--
-- Name: welcome_page_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.welcome_page_settings (
    id integer NOT NULL,
    welcome_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT one_and_only_one CHECK ((id = 0))
);


--
-- Name: api_tokens api_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_tokens
    ADD CONSTRAINT api_tokens_pkey PRIMARY KEY (id);


--
-- Name: branch_update_events branch_update_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branch_update_events
    ADD CONSTRAINT branch_update_events_pkey PRIMARY KEY (id);


--
-- Name: branches_commits branches_commits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branches_commits
    ADD CONSTRAINT branches_commits_pkey PRIMARY KEY (commit_id, branch_id);


--
-- Name: branches branches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branches
    ADD CONSTRAINT branches_pkey PRIMARY KEY (id);


--
-- Name: commits commits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commits
    ADD CONSTRAINT commits_pkey PRIMARY KEY (id);


--
-- Name: email_addresses email_addresses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_addresses
    ADD CONSTRAINT email_addresses_pkey PRIMARY KEY (email_address);


--
-- Name: events events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.events
    ADD CONSTRAINT events_pkey PRIMARY KEY (id);


--
-- Name: executor_events executor_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.executor_events
    ADD CONSTRAINT executor_events_pkey PRIMARY KEY (id);


--
-- Name: executor_issues executor_issues_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.executor_issues
    ADD CONSTRAINT executor_issues_pkey PRIMARY KEY (id);


--
-- Name: executors executors_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.executors
    ADD CONSTRAINT executors_pkey PRIMARY KEY (id);


--
-- Name: job_issues job_issues_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.job_issues
    ADD CONSTRAINT job_issues_pkey PRIMARY KEY (id);


--
-- Name: job_specifications job_specifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.job_specifications
    ADD CONSTRAINT job_specifications_pkey PRIMARY KEY (id);


--
-- Name: job_state_update_events job_state_update_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.job_state_update_events
    ADD CONSTRAINT job_state_update_events_pkey PRIMARY KEY (id);


--
-- Name: jobs jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jobs
    ADD CONSTRAINT jobs_pkey PRIMARY KEY (id);


--
-- Name: pending_create_trials_evaluations pending_create_trials_evaluations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_create_trials_evaluations
    ADD CONSTRAINT pending_create_trials_evaluations_pkey PRIMARY KEY (id);


--
-- Name: pending_job_evaluations pending_job_evaluations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_job_evaluations
    ADD CONSTRAINT pending_job_evaluations_pkey PRIMARY KEY (id);


--
-- Name: pending_result_propagations pending_result_propagations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_result_propagations
    ADD CONSTRAINT pending_result_propagations_pkey PRIMARY KEY (id);


--
-- Name: pending_task_evaluations pending_task_evaluations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_task_evaluations
    ADD CONSTRAINT pending_task_evaluations_pkey PRIMARY KEY (id);


--
-- Name: repositories repositories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repositories
    ADD CONSTRAINT repositories_pkey PRIMARY KEY (id);


--
-- Name: repository_events repository_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repository_events
    ADD CONSTRAINT repository_events_pkey PRIMARY KEY (id);


--
-- Name: repository_executor_permissions repository_executor_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repository_executor_permissions
    ADD CONSTRAINT repository_executor_permissions_pkey PRIMARY KEY (id);


--
-- Name: repository_user_permissions repository_user_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repository_user_permissions
    ADD CONSTRAINT repository_user_permissions_pkey PRIMARY KEY (id);


--
-- Name: schema_migrations schema_migrations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schema_migrations
    ADD CONSTRAINT schema_migrations_pkey PRIMARY KEY (version);


--
-- Name: script_state_update_events script_state_update_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.script_state_update_events
    ADD CONSTRAINT script_state_update_events_pkey PRIMARY KEY (id);


--
-- Name: scripts scripts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scripts
    ADD CONSTRAINT scripts_pkey PRIMARY KEY (id);


--
-- Name: submodules submodules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submodules
    ADD CONSTRAINT submodules_pkey PRIMARY KEY (commit_id, path);


--
-- Name: task_specifications task_specifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_specifications
    ADD CONSTRAINT task_specifications_pkey PRIMARY KEY (id);


--
-- Name: task_state_update_events task_state_update_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_state_update_events
    ADD CONSTRAINT task_state_update_events_pkey PRIMARY KEY (id);


--
-- Name: tasks tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tasks
    ADD CONSTRAINT tasks_pkey PRIMARY KEY (id);


--
-- Name: tree_attachments tree_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tree_attachments
    ADD CONSTRAINT tree_attachments_pkey PRIMARY KEY (id);


--
-- Name: tree_id_notifications tree_id_notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tree_id_notifications
    ADD CONSTRAINT tree_id_notifications_pkey PRIMARY KEY (id);


--
-- Name: tree_issues tree_issues_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tree_issues
    ADD CONSTRAINT tree_issues_pkey PRIMARY KEY (id);


--
-- Name: trial_attachments trial_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trial_attachments
    ADD CONSTRAINT trial_attachments_pkey PRIMARY KEY (id);


--
-- Name: trial_issues trial_issues_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trial_issues
    ADD CONSTRAINT trial_issues_pkey PRIMARY KEY (id);


--
-- Name: trial_state_update_events trial_state_update_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trial_state_update_events
    ADD CONSTRAINT trial_state_update_events_pkey PRIMARY KEY (id);


--
-- Name: trials trials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trials
    ADD CONSTRAINT trials_pkey PRIMARY KEY (id);


--
-- Name: user_events user_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_events
    ADD CONSTRAINT user_events_pkey PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: welcome_page_settings welcome_page_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.welcome_page_settings
    ADD CONSTRAINT welcome_page_settings_pkey PRIMARY KEY (id);


--
-- Name: branches_lower_name_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX branches_lower_name_idx ON public.branches USING btree (lower((name)::text));


--
-- Name: commits_to_tsvector_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX commits_to_tsvector_idx ON public.commits USING gin (to_tsvector('english'::regconfig, body));


--
-- Name: commits_to_tsvector_idx1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX commits_to_tsvector_idx1 ON public.commits USING gin (to_tsvector('english'::regconfig, (author_name)::text));


--
-- Name: commits_to_tsvector_idx2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX commits_to_tsvector_idx2 ON public.commits USING gin (to_tsvector('english'::regconfig, (author_email)::text));


--
-- Name: commits_to_tsvector_idx3; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX commits_to_tsvector_idx3 ON public.commits USING gin (to_tsvector('english'::regconfig, (committer_name)::text));


--
-- Name: commits_to_tsvector_idx4; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX commits_to_tsvector_idx4 ON public.commits USING gin (to_tsvector('english'::regconfig, (committer_email)::text));


--
-- Name: commits_to_tsvector_idx5; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX commits_to_tsvector_idx5 ON public.commits USING gin (to_tsvector('english'::regconfig, subject));


--
-- Name: commits_to_tsvector_idx6; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX commits_to_tsvector_idx6 ON public.commits USING gin (to_tsvector('english'::regconfig, body));


--
-- Name: idx_jobs_tree-id_job-specification-id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "idx_jobs_tree-id_job-specification-id" ON public.jobs USING btree (tree_id, job_specification_id);


--
-- Name: idx_jobs_tree-id_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "idx_jobs_tree-id_key" ON public.jobs USING btree (tree_id, key);


--
-- Name: idx_jobs_tree-id_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "idx_jobs_tree-id_name" ON public.jobs USING btree (tree_id, name);


--
-- Name: index_api_tokens_on_token_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX index_api_tokens_on_token_hash ON public.api_tokens USING btree (token_hash);


--
-- Name: index_branch_update_events_on_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_branch_update_events_on_created_at ON public.branch_update_events USING btree (created_at);


--
-- Name: index_branches_on_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_branches_on_name ON public.branches USING btree (name);


--
-- Name: index_branches_on_repository_id_and_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX index_branches_on_repository_id_and_name ON public.branches USING btree (repository_id, name);


--
-- Name: index_commit_arcs_on_child_id_and_parent_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_commit_arcs_on_child_id_and_parent_id ON public.commit_arcs USING btree (child_id, parent_id);


--
-- Name: index_commit_arcs_on_parent_id_and_child_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX index_commit_arcs_on_parent_id_and_child_id ON public.commit_arcs USING btree (parent_id, child_id);


--
-- Name: index_commits_on_author_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_commits_on_author_date ON public.commits USING btree (author_date);


--
-- Name: index_commits_on_committer_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_commits_on_committer_date ON public.commits USING btree (committer_date);


--
-- Name: index_commits_on_depth; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_commits_on_depth ON public.commits USING btree (depth);


--
-- Name: index_commits_on_tree_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_commits_on_tree_id ON public.commits USING btree (tree_id);


--
-- Name: index_commits_on_updated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_commits_on_updated_at ON public.commits USING btree (updated_at);


--
-- Name: index_email_addresses_on_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_email_addresses_on_user_id ON public.email_addresses USING btree (user_id);


--
-- Name: index_events_on_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_events_on_created_at ON public.events USING btree (created_at);


--
-- Name: index_events_on_operation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_events_on_operation ON public.events USING btree (operation);


--
-- Name: index_events_on_table_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_events_on_table_name ON public.events USING btree (table_name);


--
-- Name: index_executor_events_on_executor_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_executor_events_on_executor_id ON public.executor_events USING btree (executor_id);


--
-- Name: index_executor_issues_on_executor_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_executor_issues_on_executor_id ON public.executor_issues USING btree (executor_id);


--
-- Name: index_executors_on_accepted_repositories; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_executors_on_accepted_repositories ON public.executors USING btree (accepted_repositories);


--
-- Name: index_executors_on_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX index_executors_on_name ON public.executors USING btree (name);


--
-- Name: index_executors_on_token_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX index_executors_on_token_hash ON public.executors USING btree (token_hash);


--
-- Name: index_executors_on_traits; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_executors_on_traits ON public.executors USING btree (traits);


--
-- Name: index_job_issues_on_job_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_job_issues_on_job_id ON public.job_issues USING btree (job_id);


--
-- Name: index_job_state_update_events_on_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_job_state_update_events_on_created_at ON public.job_state_update_events USING btree (created_at);


--
-- Name: index_job_state_update_events_on_job_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_job_state_update_events_on_job_id ON public.job_state_update_events USING btree (job_id);


--
-- Name: index_jobs_on_job_specification_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_jobs_on_job_specification_id ON public.jobs USING btree (job_specification_id);


--
-- Name: index_jobs_on_key; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_jobs_on_key ON public.jobs USING btree (key);


--
-- Name: index_jobs_on_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_jobs_on_name ON public.jobs USING btree (name);


--
-- Name: index_jobs_on_tree_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_jobs_on_tree_id ON public.jobs USING btree (tree_id);


--
-- Name: index_pending_create_trials_evaluations_on_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_pending_create_trials_evaluations_on_created_at ON public.pending_create_trials_evaluations USING btree (created_at);


--
-- Name: index_pending_create_trials_evaluations_on_task_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_pending_create_trials_evaluations_on_task_id ON public.pending_create_trials_evaluations USING btree (task_id);


--
-- Name: index_pending_job_evaluations_on_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_pending_job_evaluations_on_created_at ON public.pending_job_evaluations USING btree (created_at);


--
-- Name: index_pending_result_propagations_on_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_pending_result_propagations_on_created_at ON public.pending_result_propagations USING btree (created_at);


--
-- Name: index_pending_task_evaluations_on_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_pending_task_evaluations_on_created_at ON public.pending_task_evaluations USING btree (created_at);


--
-- Name: index_repositories_on_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_repositories_on_created_at ON public.repositories USING btree (created_at);


--
-- Name: index_repositories_on_git_url; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX index_repositories_on_git_url ON public.repositories USING btree (git_url);


--
-- Name: index_repositories_on_update_notification_token; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_repositories_on_update_notification_token ON public.repositories USING btree (update_notification_token);


--
-- Name: index_repositories_on_updated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_repositories_on_updated_at ON public.repositories USING btree (updated_at);


--
-- Name: index_repository_events_on_repository_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_repository_events_on_repository_id ON public.repository_events USING btree (repository_id);


--
-- Name: index_script_state_update_events_on_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_script_state_update_events_on_created_at ON public.script_state_update_events USING btree (created_at);


--
-- Name: index_script_state_update_events_on_script_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_script_state_update_events_on_script_id ON public.script_state_update_events USING btree (script_id);


--
-- Name: index_scripts_on_issues; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_scripts_on_issues ON public.scripts USING btree (issues);


--
-- Name: index_scripts_on_trial_id_and_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX index_scripts_on_trial_id_and_key ON public.scripts USING btree (trial_id, key);


--
-- Name: index_submodules_on_commit_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_submodules_on_commit_id ON public.submodules USING btree (commit_id);


--
-- Name: index_submodules_on_submodule_commit_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_submodules_on_submodule_commit_id ON public.submodules USING btree (submodule_commit_id);


--
-- Name: index_task_state_update_events_on_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_task_state_update_events_on_created_at ON public.task_state_update_events USING btree (created_at);


--
-- Name: index_task_state_update_events_on_task_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_task_state_update_events_on_task_id ON public.task_state_update_events USING btree (task_id);


--
-- Name: index_tasks_on_exclusive_global_resources; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_tasks_on_exclusive_global_resources ON public.tasks USING btree (exclusive_global_resources);


--
-- Name: index_tasks_on_job_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_tasks_on_job_id ON public.tasks USING btree (job_id);


--
-- Name: index_tasks_on_job_id_and_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX index_tasks_on_job_id_and_name ON public.tasks USING btree (job_id, name);


--
-- Name: index_tasks_on_task_specification_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_tasks_on_task_specification_id ON public.tasks USING btree (task_specification_id);


--
-- Name: index_tasks_on_traits; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_tasks_on_traits ON public.tasks USING btree (traits);


--
-- Name: index_tree_attachments_on_tree_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_tree_attachments_on_tree_id ON public.tree_attachments USING btree (tree_id);


--
-- Name: index_tree_attachments_on_tree_id_and_path; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX index_tree_attachments_on_tree_id_and_path ON public.tree_attachments USING btree (tree_id, path);


--
-- Name: index_tree_issues_on_tree_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_tree_issues_on_tree_id ON public.tree_issues USING btree (tree_id);


--
-- Name: index_trial_attachments_on_trial_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_trial_attachments_on_trial_id ON public.trial_attachments USING btree (trial_id);


--
-- Name: index_trial_attachments_on_trial_id_and_path; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX index_trial_attachments_on_trial_id_and_path ON public.trial_attachments USING btree (trial_id, path);


--
-- Name: index_trial_issues_on_trial_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_trial_issues_on_trial_id ON public.trial_issues USING btree (trial_id);


--
-- Name: index_trial_state_update_events_on_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_trial_state_update_events_on_created_at ON public.trial_state_update_events USING btree (created_at);


--
-- Name: index_trial_state_update_events_on_trial_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_trial_state_update_events_on_trial_id ON public.trial_state_update_events USING btree (trial_id);


--
-- Name: index_trials_on_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_trials_on_state ON public.trials USING btree (state);


--
-- Name: index_trials_on_task_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_trials_on_task_id ON public.trials USING btree (task_id);


--
-- Name: index_user_events_on_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_user_events_on_user_id ON public.user_events USING btree (user_id);


--
-- Name: user_lower_login_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_lower_login_idx ON public.users USING btree (lower((login)::text));


--
-- Name: job_cache_signatures _RETURN; Type: RULE; Schema: public; Owner: -
--

CREATE OR REPLACE VIEW public.job_cache_signatures AS
 SELECT jobs.id AS job_id,
    md5(string_agg(DISTINCT (branches.updated_at)::text, ',
             '::text ORDER BY (branches.updated_at)::text)) AS branches_signature,
    md5(string_agg(DISTINCT (commits.updated_at)::text, ',
             '::text ORDER BY (commits.updated_at)::text)) AS commits_signature,
    md5(string_agg(DISTINCT (job_issues.updated_at)::text, ',
             '::text ORDER BY (job_issues.updated_at)::text)) AS job_issues_signature,
    count(DISTINCT job_issues.*) AS job_issues_count,
    md5(string_agg(DISTINCT (repositories.updated_at)::text, ',
             '::text ORDER BY (repositories.updated_at)::text)) AS repositories_signature,
    ( SELECT (((count(DISTINCT tasks.id))::text || ' - '::text) || (max(tasks.updated_at))::text)
           FROM public.tasks
          WHERE (tasks.job_id = jobs.id)) AS tasks_signature,
    ( SELECT count(DISTINCT tree_attachments.id) AS count
           FROM public.tree_attachments
          WHERE (tree_attachments.tree_id = (jobs.tree_id)::text)) AS tree_attachments_count
   FROM (((((public.jobs
     LEFT JOIN public.job_issues ON ((jobs.id = job_issues.job_id)))
     LEFT JOIN public.commits ON (((jobs.tree_id)::text = (commits.tree_id)::text)))
     LEFT JOIN public.branches_commits ON (((branches_commits.commit_id)::text = (commits.id)::text)))
     LEFT JOIN public.branches ON ((branches_commits.branch_id = branches.id)))
     LEFT JOIN public.repositories ON ((branches.repository_id = repositories.id)))
  GROUP BY jobs.id;


--
-- Name: branch_update_events clean_branch_update_events; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER clean_branch_update_events AFTER INSERT ON public.branch_update_events FOR EACH STATEMENT EXECUTE PROCEDURE public.clean_branch_update_events();


--
-- Name: executor_events clean_executor_events; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER clean_executor_events AFTER INSERT ON public.executor_events FOR EACH STATEMENT EXECUTE PROCEDURE public.clean_executor_events();


--
-- Name: job_state_update_events clean_job_state_update_events; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER clean_job_state_update_events AFTER INSERT ON public.job_state_update_events FOR EACH STATEMENT EXECUTE PROCEDURE public.clean_job_state_update_events();


--
-- Name: repository_events clean_repository_events; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER clean_repository_events AFTER INSERT ON public.repository_events FOR EACH STATEMENT EXECUTE PROCEDURE public.clean_repository_events();


--
-- Name: script_state_update_events clean_script_state_update_events; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER clean_script_state_update_events AFTER INSERT ON public.script_state_update_events FOR EACH STATEMENT EXECUTE PROCEDURE public.clean_script_state_update_events();


--
-- Name: task_state_update_events clean_task_state_update_events; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER clean_task_state_update_events AFTER INSERT ON public.task_state_update_events FOR EACH STATEMENT EXECUTE PROCEDURE public.clean_task_state_update_events();


--
-- Name: trial_state_update_events clean_trial_state_update_events; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER clean_trial_state_update_events AFTER INSERT ON public.trial_state_update_events FOR EACH STATEMENT EXECUTE PROCEDURE public.clean_trial_state_update_events();


--
-- Name: user_events clean_user_events; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER clean_user_events AFTER INSERT ON public.user_events FOR EACH STATEMENT EXECUTE PROCEDURE public.clean_user_events();


--
-- Name: branches create_branch_update_event; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_branch_update_event AFTER INSERT OR UPDATE ON public.branches FOR EACH ROW EXECUTE PROCEDURE public.create_branch_update_event();


--
-- Name: branches create_event_on_branches_operation; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_event_on_branches_operation AFTER INSERT OR DELETE OR UPDATE ON public.branches FOR EACH ROW EXECUTE PROCEDURE public.create_event();


--
-- Name: jobs create_event_on_jobs_operation; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_event_on_jobs_operation AFTER INSERT OR DELETE OR UPDATE ON public.jobs FOR EACH ROW EXECUTE PROCEDURE public.create_event();


--
-- Name: tree_attachments create_event_on_tree_attachments_operation; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_event_on_tree_attachments_operation AFTER INSERT OR DELETE OR UPDATE ON public.tree_attachments FOR EACH ROW EXECUTE PROCEDURE public.create_event();


--
-- Name: jobs create_job_state_update_events_on_insert; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_job_state_update_events_on_insert AFTER INSERT ON public.jobs FOR EACH ROW EXECUTE PROCEDURE public.create_job_state_update_events();


--
-- Name: jobs create_job_state_update_events_on_update; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_job_state_update_events_on_update AFTER UPDATE ON public.jobs FOR EACH ROW WHEN (((old.state)::text IS DISTINCT FROM (new.state)::text)) EXECUTE PROCEDURE public.create_job_state_update_events();


--
-- Name: tasks create_pending_create_trials_evaluations_on_tasks_insert; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_pending_create_trials_evaluations_on_tasks_insert AFTER INSERT ON public.tasks FOR EACH ROW EXECUTE PROCEDURE public.create_pending_create_trials_evaluations_on_tasks_insert();


--
-- Name: trial_state_update_events create_pending_create_trials_evaluations_on_trial_state_change; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_pending_create_trials_evaluations_on_trial_state_change AFTER INSERT ON public.trial_state_update_events FOR EACH ROW WHEN (((new.state)::text = ANY (ARRAY[('aborted'::character varying)::text, ('defective'::character varying)::text, ('failed'::character varying)::text, ('passed'::character varying)::text, ('skipped'::character varying)::text]))) EXECUTE PROCEDURE public.create_pending_create_trials_evaluations_on_trial_state_change();


--
-- Name: task_state_update_events create_pending_job_evaluation_on_task_state_update_event_insert; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_pending_job_evaluation_on_task_state_update_event_insert AFTER INSERT ON public.task_state_update_events FOR EACH ROW EXECUTE PROCEDURE public.create_pending_job_evaluation_on_task_state_update_event_insert();


--
-- Name: trials create_pending_result_propagation; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_pending_result_propagation AFTER UPDATE ON public.trials FOR EACH ROW WHEN ((old.result IS DISTINCT FROM new.result)) EXECUTE PROCEDURE public.create_pending_result_propagation();


--
-- Name: trial_state_update_events create_pending_task_evaluation_on_trial_state_update_event_inse; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_pending_task_evaluation_on_trial_state_update_event_inse AFTER INSERT ON public.trial_state_update_events FOR EACH ROW EXECUTE PROCEDURE public.create_pending_task_evaluation_on_trial_state_update_event_inse();


--
-- Name: scripts create_script_state_update_events_on_insert; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_script_state_update_events_on_insert AFTER INSERT ON public.scripts FOR EACH ROW EXECUTE PROCEDURE public.create_script_state_update_events();


--
-- Name: scripts create_script_state_update_events_on_update; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_script_state_update_events_on_update AFTER UPDATE ON public.scripts FOR EACH ROW WHEN (((old.state)::text IS DISTINCT FROM (new.state)::text)) EXECUTE PROCEDURE public.create_script_state_update_events();


--
-- Name: tasks create_task_state_update_events_on_insert; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_task_state_update_events_on_insert AFTER INSERT ON public.tasks FOR EACH ROW EXECUTE PROCEDURE public.create_task_state_update_events();


--
-- Name: tasks create_task_state_update_events_on_update; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_task_state_update_events_on_update AFTER UPDATE ON public.tasks FOR EACH ROW WHEN (((old.state)::text IS DISTINCT FROM (new.state)::text)) EXECUTE PROCEDURE public.create_task_state_update_events();


--
-- Name: branches create_tree_id_notification_on_branch_change; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_tree_id_notification_on_branch_change AFTER INSERT OR UPDATE ON public.branches FOR EACH ROW EXECUTE PROCEDURE public.create_tree_id_notification_on_branch_change();


--
-- Name: jobs create_tree_id_notification_on_job_state_change; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_tree_id_notification_on_job_state_change AFTER UPDATE ON public.jobs FOR EACH ROW WHEN (((old.state)::text IS DISTINCT FROM (new.state)::text)) EXECUTE PROCEDURE public.create_tree_id_notification_on_job_state_change();


--
-- Name: trials create_trial_state_update_events_on_insert; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_trial_state_update_events_on_insert AFTER INSERT ON public.trials FOR EACH ROW EXECUTE PROCEDURE public.create_trial_state_update_events();


--
-- Name: trials create_trial_state_update_events_on_update; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER create_trial_state_update_events_on_update AFTER UPDATE ON public.trials FOR EACH ROW WHEN (((old.state)::text IS DISTINCT FROM (new.state)::text)) EXECUTE PROCEDURE public.create_trial_state_update_events();


--
-- Name: executors executor_event; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER executor_event AFTER INSERT OR DELETE OR UPDATE ON public.executors FOR EACH ROW EXECUTE PROCEDURE public.executor_event();


--
-- Name: executors executor_event_truncate; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER executor_event_truncate AFTER TRUNCATE ON public.executors FOR EACH STATEMENT EXECUTE PROCEDURE public.executor_event();


--
-- Name: repositories repository_event; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER repository_event AFTER INSERT OR DELETE OR UPDATE ON public.repositories FOR EACH ROW EXECUTE PROCEDURE public.repository_event();


--
-- Name: repositories repository_event_truncate; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER repository_event_truncate AFTER TRUNCATE ON public.repositories FOR EACH STATEMENT EXECUTE PROCEDURE public.repository_event();


--
-- Name: api_tokens update_updated_at_column_of_api_tokens; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_api_tokens BEFORE UPDATE ON public.api_tokens FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: branches update_updated_at_column_of_branches; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_branches BEFORE UPDATE ON public.branches FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: commits update_updated_at_column_of_commits; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_commits BEFORE UPDATE ON public.commits FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: executor_issues update_updated_at_column_of_executor_issues; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_executor_issues BEFORE UPDATE ON public.executor_issues FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: executors update_updated_at_column_of_executors; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_executors BEFORE UPDATE ON public.executors FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: job_issues update_updated_at_column_of_job_issues; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_job_issues BEFORE UPDATE ON public.job_issues FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: jobs update_updated_at_column_of_jobs; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_jobs BEFORE UPDATE ON public.jobs FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: repositories update_updated_at_column_of_repositories; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_repositories BEFORE UPDATE ON public.repositories FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: scripts update_updated_at_column_of_scripts; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_scripts BEFORE UPDATE ON public.scripts FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: tasks update_updated_at_column_of_tasks; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_tasks BEFORE UPDATE ON public.tasks FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: tree_attachments update_updated_at_column_of_tree_attachments; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_tree_attachments BEFORE UPDATE ON public.tree_attachments FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: tree_id_notifications update_updated_at_column_of_tree_id_notifications; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_tree_id_notifications BEFORE UPDATE ON public.tree_id_notifications FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: tree_issues update_updated_at_column_of_tree_issues; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_tree_issues BEFORE UPDATE ON public.tree_issues FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: trial_attachments update_updated_at_column_of_trial_attachments; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_trial_attachments BEFORE UPDATE ON public.trial_attachments FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: trial_issues update_updated_at_column_of_trial_issues; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_trial_issues BEFORE UPDATE ON public.trial_issues FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: trials update_updated_at_column_of_trials; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_trials BEFORE UPDATE ON public.trials FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: users update_updated_at_column_of_users; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_users BEFORE UPDATE ON public.users FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: welcome_page_settings update_updated_at_column_of_welcome_page_settings; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_updated_at_column_of_welcome_page_settings BEFORE UPDATE ON public.welcome_page_settings FOR EACH ROW WHEN ((old.* IS DISTINCT FROM new.*)) EXECUTE PROCEDURE public.update_updated_at_column();


--
-- Name: users user_event; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER user_event AFTER INSERT OR DELETE OR UPDATE ON public.users FOR EACH ROW EXECUTE PROCEDURE public.user_event();


--
-- Name: users user_event_truncate; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER user_event_truncate AFTER TRUNCATE ON public.users FOR EACH STATEMENT EXECUTE PROCEDURE public.user_event();


--
-- Name: pending_job_evaluations fk_rails_0bf999a237; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_job_evaluations
    ADD CONSTRAINT fk_rails_0bf999a237 FOREIGN KEY (task_state_update_event_id) REFERENCES public.task_state_update_events(id) ON DELETE CASCADE;


--
-- Name: branch_update_events fk_rails_1aba877542; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branch_update_events
    ADD CONSTRAINT fk_rails_1aba877542 FOREIGN KEY (branch_id) REFERENCES public.branches(id) ON DELETE CASCADE;


--
-- Name: pending_task_evaluations fk_rails_2043ecf4ac; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_task_evaluations
    ADD CONSTRAINT fk_rails_2043ecf4ac FOREIGN KEY (task_id) REFERENCES public.tasks(id) ON DELETE CASCADE;


--
-- Name: pending_task_evaluations fk_rails_2285e098a6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_task_evaluations
    ADD CONSTRAINT fk_rails_2285e098a6 FOREIGN KEY (trial_state_update_event_id) REFERENCES public.trial_state_update_events(id) ON DELETE CASCADE;


--
-- Name: task_state_update_events fk_rails_2420fea61c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_state_update_events
    ADD CONSTRAINT fk_rails_2420fea61c FOREIGN KEY (task_id) REFERENCES public.tasks(id) ON DELETE CASCADE;


--
-- Name: trial_attachments fk_rails_2595d4f43b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trial_attachments
    ADD CONSTRAINT fk_rails_2595d4f43b FOREIGN KEY (trial_id) REFERENCES public.trials(id) ON DELETE CASCADE;


--
-- Name: trials fk_rails_3bfb7b73f7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trials
    ADD CONSTRAINT fk_rails_3bfb7b73f7 FOREIGN KEY (aborted_by) REFERENCES public.users(id);


--
-- Name: jobs fk_rails_3ccf965e25; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jobs
    ADD CONSTRAINT fk_rails_3ccf965e25 FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: trials fk_rails_3e557ab362; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trials
    ADD CONSTRAINT fk_rails_3e557ab362 FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: jobs fk_rails_5056f0a1f0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jobs
    ADD CONSTRAINT fk_rails_5056f0a1f0 FOREIGN KEY (aborted_by) REFERENCES public.users(id);


--
-- Name: repository_user_permissions fk_rails_50624d2f4e; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repository_user_permissions
    ADD CONSTRAINT fk_rails_50624d2f4e FOREIGN KEY (repository_id) REFERENCES public.repositories(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: script_state_update_events fk_rails_5ff6d4badd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.script_state_update_events
    ADD CONSTRAINT fk_rails_5ff6d4badd FOREIGN KEY (script_id) REFERENCES public.scripts(id) ON DELETE CASCADE;


--
-- Name: pending_job_evaluations fk_rails_613c47280f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_job_evaluations
    ADD CONSTRAINT fk_rails_613c47280f FOREIGN KEY (job_id) REFERENCES public.jobs(id) ON DELETE CASCADE;


--
-- Name: commit_arcs fk_rails_637f302c5b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commit_arcs
    ADD CONSTRAINT fk_rails_637f302c5b FOREIGN KEY (parent_id) REFERENCES public.commits(id) ON DELETE CASCADE;


--
-- Name: submodules fk_rails_73565c5700; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submodules
    ADD CONSTRAINT fk_rails_73565c5700 FOREIGN KEY (commit_id) REFERENCES public.commits(id) ON DELETE CASCADE;


--
-- Name: branches fk_rails_741467517e; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branches
    ADD CONSTRAINT fk_rails_741467517e FOREIGN KEY (current_commit_id) REFERENCES public.commits(id) ON DELETE CASCADE;


--
-- Name: pending_result_propagations fk_rails_870c3ec6fd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_result_propagations
    ADD CONSTRAINT fk_rails_870c3ec6fd FOREIGN KEY (trial_id) REFERENCES public.trials(id) ON DELETE CASCADE;


--
-- Name: executor_issues fk_rails_880255918f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.executor_issues
    ADD CONSTRAINT fk_rails_880255918f FOREIGN KEY (executor_id) REFERENCES public.executors(id) ON DELETE CASCADE;


--
-- Name: pending_create_trials_evaluations fk_rails_99e0f3714e; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_create_trials_evaluations
    ADD CONSTRAINT fk_rails_99e0f3714e FOREIGN KEY (trial_state_update_event_id) REFERENCES public.trial_state_update_events(id) ON DELETE CASCADE;


--
-- Name: job_state_update_events fk_rails_b33ab63674; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.job_state_update_events
    ADD CONSTRAINT fk_rails_b33ab63674 FOREIGN KEY (job_id) REFERENCES public.jobs(id) ON DELETE CASCADE;


--
-- Name: repository_executor_permissions fk_rails_bdef0ebb7b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repository_executor_permissions
    ADD CONSTRAINT fk_rails_bdef0ebb7b FOREIGN KEY (repository_id) REFERENCES public.repositories(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: branches_commits fk_rails_ce2b80387a; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branches_commits
    ADD CONSTRAINT fk_rails_ce2b80387a FOREIGN KEY (commit_id) REFERENCES public.commits(id) ON DELETE CASCADE;


--
-- Name: branches fk_rails_ce3c7008c0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branches
    ADD CONSTRAINT fk_rails_ce3c7008c0 FOREIGN KEY (repository_id) REFERENCES public.repositories(id) ON DELETE CASCADE;


--
-- Name: jobs fk_rails_cf50105b6a; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jobs
    ADD CONSTRAINT fk_rails_cf50105b6a FOREIGN KEY (resumed_by) REFERENCES public.users(id);


--
-- Name: repository_user_permissions fk_rails_d503147ced; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repository_user_permissions
    ADD CONSTRAINT fk_rails_d503147ced FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: trial_state_update_events fk_rails_dbbb93c299; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trial_state_update_events
    ADD CONSTRAINT fk_rails_dbbb93c299 FOREIGN KEY (trial_id) REFERENCES public.trials(id) ON DELETE CASCADE;


--
-- Name: email_addresses fk_rails_de643267e7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_addresses
    ADD CONSTRAINT fk_rails_de643267e7 FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: repository_executor_permissions fk_rails_e57d2c8dc2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repository_executor_permissions
    ADD CONSTRAINT fk_rails_e57d2c8dc2 FOREIGN KEY (executor_id) REFERENCES public.executors(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: scripts fk_rails_eb81826b6c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scripts
    ADD CONSTRAINT fk_rails_eb81826b6c FOREIGN KEY (trial_id) REFERENCES public.trials(id) ON DELETE CASCADE;


--
-- Name: pending_create_trials_evaluations fk_rails_f0d4638ea2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_create_trials_evaluations
    ADD CONSTRAINT fk_rails_f0d4638ea2 FOREIGN KEY (task_id) REFERENCES public.tasks(id) ON DELETE CASCADE;


--
-- Name: api_tokens fk_rails_f16b5e0447; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_tokens
    ADD CONSTRAINT fk_rails_f16b5e0447 FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: branches_commits fk_rails_f1b0bc6b0c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branches_commits
    ADD CONSTRAINT fk_rails_f1b0bc6b0c FOREIGN KEY (branch_id) REFERENCES public.branches(id) ON DELETE CASCADE;


--
-- Name: commit_arcs fk_rails_fe00cc3459; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commit_arcs
    ADD CONSTRAINT fk_rails_fe00cc3459 FOREIGN KEY (child_id) REFERENCES public.commits(id) ON DELETE CASCADE;


--
-- Name: job_issues job_issues_jobs_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.job_issues
    ADD CONSTRAINT job_issues_jobs_fkey FOREIGN KEY (job_id) REFERENCES public.jobs(id) ON DELETE CASCADE;


--
-- Name: jobs jobs_job-specifications_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jobs
    ADD CONSTRAINT "jobs_job-specifications_fkey" FOREIGN KEY (job_specification_id) REFERENCES public.job_specifications(id);


--
-- Name: tasks tasks_jobs_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tasks
    ADD CONSTRAINT tasks_jobs_fkey FOREIGN KEY (job_id) REFERENCES public.jobs(id) ON DELETE CASCADE;


--
-- Name: trial_issues trial_issues_trials_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trial_issues
    ADD CONSTRAINT trial_issues_trials_fkey FOREIGN KEY (trial_id) REFERENCES public.trials(id) ON DELETE CASCADE;


--
-- Name: trials trials_tasks_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trials
    ADD CONSTRAINT trials_tasks_fkey FOREIGN KEY (task_id) REFERENCES public.tasks(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

-- ----------------------------------------------------------------------------
--  Migrations ----------------------------------------------------------------
-- ----------------------------------------------------------------------------
--
-- PostgreSQL database dump
--

-- Dumped from database version 10.19
-- Dumped by pg_dump version 10.19

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: schema_migrations; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.schema_migrations (version) VALUES ('433');
INSERT INTO public.schema_migrations (version) VALUES ('434');
INSERT INTO public.schema_migrations (version) VALUES ('435');
INSERT INTO public.schema_migrations (version) VALUES ('436');


--
-- PostgreSQL database dump complete
--

