; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.migrations.438
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [clojure.java.jdbc :as jdbc]
    ))

(defn up [tx]
  (jdbc/execute!
    tx
    (->> [
          " ALTER TABLE commits ADD COLUMN signed_message text;
            ALTER TABLE commits ADD COLUMN signature text; "

          ; pgp_pub_keys ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

          " CREATE TABLE pgp_pub_keys (
            id uuid DEFAULT uuid_generate_v4() NOT NULL,
            key text NOT NULL,
            user_id uuid NOT NULL);

          ALTER TABLE ONLY pgp_pub_keys
            ADD CONSTRAINT pgp_pub_keys_pkey PRIMARY KEY (id);

          CREATE INDEX idx_user_id ON pgp_pub_keys USING btree (user_id);

          ALTER TABLE ONLY pgp_pub_keys
            ADD CONSTRAINT fkey_pgp_pub_keys_user_id FOREIGN KEY (user_id)
              REFERENCES users(id)
              ON UPDATE CASCADE
              ON DELETE CASCADE; "

          ; pgp_pub_key_fingerprints ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

          " CREATE TABLE pgp_pub_key_fingerprints (
            fingerprint text NOT NULL,
            pgp_pub_key_id uuid NOT NULL);

          ALTER TABLE ONLY pgp_pub_key_fingerprints
            ADD CONSTRAINT pgp_pub_key_fingerprints_pkey PRIMARY KEY (fingerprint);

          ALTER TABLE ONLY pgp_pub_key_fingerprints
            ADD CONSTRAINT fkey_pgp_pub_key_fingerprints_key FOREIGN KEY (pgp_pub_key_id)
              REFERENCES pgp_pub_keys(id)
              ON UPDATE CASCADE
              ON DELETE CASCADE; "

          ; signatures ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

          " CREATE TABLE signatures (
            id uuid DEFAULT uuid_generate_v4() NOT NULL,
            tree_id character varying(40) NOT NULL,
            message text NOT NULL,
            signature text NOT NULL,
            fingerprint text NOT NULL);

          ALTER TABLE ONLY signatures
            ADD CONSTRAINT signatures_pkey PRIMARY KEY (id);

          CREATE INDEX idx_tree_id ON signatures USING btree (tree_id);

          ALTER TABLE ONLY signatures
            ADD CONSTRAINT fkey_pgp_pub_key_fingerprint FOREIGN KEY (fingerprint)
              REFERENCES pgp_pub_key_fingerprints(fingerprint)
              ON UPDATE CASCADE
              ON DELETE CASCADE; "

          ]
         (clojure.string/join \newline))
    ))

(defn down [tx]
  (jdbc/execute!
    tx
    (->> [
          "
          ALTER TABLE commits DROP COLUMN signed_message;
          ALTER TABLE commits DROP COLUMN signature ;
          "]
         (clojure.string/join \newline))))

