; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.migrations.436
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [clojure.java.jdbc :as jdbc]
    ))

(defn up [tx]
  (jdbc/execute!
    tx "CREATE TRIGGER create_event_on_tree_attachments_operation
       AFTER INSERT OR UPDATE OR DELETE ON tree_attachments
       FOR EACH ROW EXECUTE PROCEDURE create_event();"))

(defn down [tx]
  (jdbc/execute!
    tx "DROP TRIGGER create_event_on_tree_attachments_operation
       ON tree_attachments;"))
