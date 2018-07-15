
CREATE TABLE projects ( 
  id varchar NOT NULL,
  name character varying NOT NULL, 
  public_view_permission boolean DEFAULT false,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  branch_trigger_enabled boolean DEFAULT false,
  cron_trigger_enabled boolean DEFAULT false,
  CONSTRAINT id_simple CHECK ((id ~ '^[a-z][a-z0-9\-_]+$'::text))
);

CREATE TRIGGER update_updated_at_column_of_projects
  BEFORE UPDATE ON projects
  FOR EACH ROW 
  WHEN ((old.* IS DISTINCT FROM new.*)) 
  EXECUTE PROCEDURE update_updated_at_column();

ALTER TABLE ONLY projects ADD CONSTRAINT projects_pkey PRIMARY KEY (id);


-- branches --------------------------------------------------------------------

CREATE TABLE branches ( 
  id uuid DEFAULT uuid_generate_v4() NOT NULL,
  project_id varchar NOT NULL,
  name character varying NOT NULL,
  current_commit_id character varying(40) NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL);

ALTER TABLE ONLY branches ADD CONSTRAINT branches_pkey PRIMARY KEY (id);

CREATE UNIQUE INDEX index_branches_on_project_id_and_name ON branches USING btree (project_id, name);

ALTER TABLE ONLY branches ADD CONSTRAINT fkey_branches_projects
  FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

CREATE TRIGGER create_event_on_branches_operation
  AFTER INSERT OR UPDATE OR DELETE ON branches
  FOR EACH ROW EXECUTE PROCEDURE create_event();


-- commits ---------------------------------------------------------------------

CREATE TABLE commits ( 
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
  updated_at timestamp with time zone DEFAULT now() NOT NULL);


ALTER TABLE ONLY commits ADD CONSTRAINT commits_pkey PRIMARY KEY (id);

CREATE INDEX commits_to_tsvector_idx ON commits USING gin (to_tsvector('english'::regconfig, body));
CREATE INDEX commits_to_tsvector_idx1 ON commits USING gin (to_tsvector('english'::regconfig, (author_name)::text));
CREATE INDEX commits_to_tsvector_idx2 ON commits USING gin (to_tsvector('english'::regconfig, (author_email)::text));
CREATE INDEX commits_to_tsvector_idx3 ON commits USING gin (to_tsvector('english'::regconfig, (committer_name)::text));
CREATE INDEX commits_to_tsvector_idx4 ON commits USING gin (to_tsvector('english'::regconfig, (committer_email)::text));
CREATE INDEX commits_to_tsvector_idx5 ON commits USING gin (to_tsvector('english'::regconfig, subject));
CREATE INDEX commits_to_tsvector_idx6 ON commits USING gin (to_tsvector('english'::regconfig, body));

CREATE INDEX index_commits_on_author_date ON commits USING btree (author_date);
CREATE INDEX index_commits_on_committer_date ON commits USING btree (committer_date);
CREATE INDEX index_commits_on_depth ON commits USING btree (depth);
CREATE INDEX index_commits_on_tree_id ON commits USING btree (tree_id);
CREATE INDEX index_commits_on_updated_at ON commits USING btree (updated_at);


CREATE TABLE commit_arcs ( 
  parent_id character varying(40) NOT NULL,
  child_id character varying(40) NOT NULL);

CREATE INDEX index_commit_arcs_on_child_id_and_parent_id ON commit_arcs USING btree (child_id, parent_id);
CREATE UNIQUE INDEX index_commit_arcs_on_parent_id_and_child_id ON commit_arcs USING btree (parent_id, child_id);


ALTER TABLE ONLY commit_arcs ADD CONSTRAINT fkey_commit_arcs_parent_id_commits
    FOREIGN KEY (parent_id) REFERENCES commits(id) ON DELETE CASCADE;


ALTER TABLE ONLY commit_arcs ADD CONSTRAINT fkey_commit_arcs_child_id_commits 
    FOREIGN KEY (child_id) REFERENCES commits(id) ON DELETE CASCADE;


CREATE FUNCTION with_ancestors(character varying) 
RETURNS TABLE(ancestor_id character varying) LANGUAGE sql AS $_$
  WITH RECURSIVE arcs(parent_id,child_id) AS
  (SELECT $1::varchar, NULL::varchar
    UNION
    SELECT commit_arcs.* FROM commit_arcs, arcs WHERE arcs.parent_id = commit_arcs.child_id)
  SELECT parent_id FROM arcs
$_$;


CREATE FUNCTION with_descendants(character varying) 
RETURNS TABLE(descendant_id character varying) LANGUAGE sql AS $_$
  WITH RECURSIVE arcs(parent_id,child_id) AS
  (SELECT NULL::varchar, $1::varchar
    UNION
    SELECT commit_arcs.* FROM commit_arcs, arcs WHERE arcs.child_id = commit_arcs.parent_id)
  SELECT child_id FROM arcs
$_$;


-- branches commits ------------------------------------------------------------

CREATE TABLE branches_commits ( 
  branch_id uuid NOT NULL,
  commit_id character varying(40) NOT NULL);

ALTER TABLE ONLY branches_commits ADD CONSTRAINT branches_commits_pkey 
  PRIMARY KEY (commit_id, branch_id);



ALTER TABLE ONLY branches ADD CONSTRAINT fkey_branches_commits
  FOREIGN KEY (current_commit_id) REFERENCES commits(id) ON DELETE CASCADE;

ALTER TABLE ONLY branches_commits ADD CONSTRAINT fkey_branches_commits_commits
    FOREIGN KEY (commit_id) REFERENCES commits(id) ON DELETE CASCADE;

ALTER TABLE ONLY branches_commits ADD CONSTRAINT branches_commits_branches
  FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE;



CREATE FUNCTION fast_forward_ancestors_to_be_added_to_branches_commits(branch_id uuid, commit_id character varying) 
  RETURNS TABLE(branch_id uuid, commit_id character varying) 
  LANGUAGE sql AS $_$
    WITH RECURSIVE arcs(parent_id,child_id) AS
    (SELECT $2::varchar, NULL::varchar
      UNION
      SELECT commit_arcs.* FROM commit_arcs, arcs
      WHERE arcs.parent_id = commit_arcs.child_id
      AND NOT EXISTS (SELECT 1 FROM branches_commits WHERE commit_id = arcs.parent_id AND branch_id = $1))
    SELECT DISTINCT $1, parent_id FROM arcs
      WHERE NOT EXISTS (SELECT * FROM branches_commits WHERE commit_id = parent_id AND branch_id = $1)
$_$;

CREATE FUNCTION add_fast_forward_ancestors_to_branches_commits(branch_id uuid, commit_id character varying) 
  RETURNS void LANGUAGE sql AS $$
    INSERT INTO branches_commits (branch_id,commit_id)
    SELECT * FROM fast_forward_ancestors_to_be_added_to_branches_commits(branch_id,commit_id)
$$;


CREATE FUNCTION is_ancestor(node character varying, possible_ancestor character varying) 
  RETURNS boolean LANGUAGE sql AS $_$
    SELECT( 
      EXISTS (SELECT * FROM with_ancestors(node) 
                WHERE ancestor_id = possible_ancestor)
      AND $1 <> $2 )
$_$;


CREATE FUNCTION update_branches_commits()
  RETURNS trigger 
  LANGUAGE plpgsql AS $$
  BEGIN 

    -- handle non fast forward update case
    -- by removing all ancestors of old_commit_id which are not ancestors of new_commit_id
    IF ((TG_OP = 'UPDATE') 
          AND (NOT is_ancestor(NEW.current_commit_id, OLD.current_commit_id))) THEN
      DELETE FROM branches_commits
      WHERE branches_commits.branch_id = NEW.id
        AND branches_commits.commit_id IN ( 
            SELECT * FROM with_ancestors(OLD.current_commit_id)
            EXCEPT SELECT * from with_ancestors(NEW.current_commit_id));
    END IF;

    PERFORM add_fast_forward_ancestors_to_branches_commits(NEW.id, NEW.current_commit_id);

    RETURN NULL;
  END
$$;


CREATE TRIGGER update_branches_commits 
  AFTER INSERT OR UPDATE ON branches
  FOR EACH ROW EXECUTE PROCEDURE update_branches_commits();



-- submodules ------------------------------------------------------------------

CREATE TABLE submodules ( 
  submodule_commit_id character varying(40) NOT NULL,
  path text NOT NULL,
  commit_id character varying(40) NOT NULL);

ALTER TABLE ONLY submodules ADD CONSTRAINT submodules_pkey 
  PRIMARY KEY (commit_id, path);

CREATE INDEX index_submodules_on_commit_id ON submodules USING btree (commit_id);

CREATE INDEX index_submodules_on_submodule_commit_id ON submodules USING btree (submodule_commit_id);




-- gpg signatures --------------------------------------------------------------

CREATE TABLE tree_signatures ( 
  id uuid DEFAULT uuid_generate_v4() NOT NULL,
  tree_id character varying(40),
  fingerprint text,
  message text NOT NULL,
  signature text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY tree_signatures ADD CONSTRAINT pkey_tree_signatures PRIMARY KEY (id);

CREATE INDEX idx_tree_signatures_tree_id ON tree_signatures USING btree (tree_id);

CREATE INDEX idx_tree_signatures_fingerprint ON tree_signatures USING btree (fingerprint);

CREATE UNIQUE INDEX idx_tree_signatures_treeid_msg_sig ON tree_signatures USING btree (tree_id, message, signature);
