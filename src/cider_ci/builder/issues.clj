; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.issues
  (:require
    [cider-ci.utils.jdbc :refer [insert-or-update]]
    [cider-ci.utils.rdbms :as rdbms]

    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]
    [logbug.catcher :as catcher]
    [clj-uuid]

    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.debug :as debug]
    ))


;##############################################################################

(defn refcol [prefix]
  (keyword (str prefix "_id")))

(defn issue-for-generic-exception [prefix fref ex]
  (let [
        data (ex-data ex)
        class-name (-> ex .getClass .getName)
        id (clj-uuid/v5 clj-uuid/+null+ (str fref class-name data))]
    {:id id
     (refcol prefix) fref
     :title "Error"
     :description
     (->> [(str "# " (.getMessage ex))
           "```"
           (str data)
           "```"] (clojure.string/join "\\n" ))}))

(defn issue-for-ex-info [prefix fref ex]
  (let [data (ex-data ex)
        id (clj-uuid/v5 clj-uuid/+null+ (str fref data))]
    (-> (merge
          {:title (.getMessage ex)
           :description "See the builder logs for details."
           (refcol prefix) fref
           :id id}
          data))))


(defn create-issue [prefix fref ex]
  (let [issue (cond
                (instance? clojure.lang.ExceptionInfo ex) (issue-for-ex-info prefix fref ex)
                :else (issue-for-generic-exception prefix fref ex))]
    (insert-or-update
      (rdbms/get-ds)
      (str prefix "_issues")
      ["tree_id = ? AND id = ?" fref (:id issue)]
      (select-keys issue [:id (refcol prefix) :title :message :description :type]))))

