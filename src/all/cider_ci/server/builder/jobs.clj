; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.builder.jobs
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.builder.issues :as issues]
    [cider-ci.server.builder.jobs.dependencies :as jobs.dependencies]
    [cider-ci.server.builder.jobs.normalizer :as normalizer]
    [cider-ci.server.builder.jobs.tasks-generator :as tasks-generator]
    [cider-ci.server.builder.jobs.validator.job :as job-validator]

    [cider-ci.server.builder.project-configuration :as project-configuration]
    [cider-ci.server.builder.repository :as repository]
    [cider-ci.server.builder.spec :as spec]
    [cider-ci.server.builder.tasks :as tasks]

    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    ))


;### create job #########################################################

(defn raw-job-spec [tree-id job-key]
  (catcher/with-logging {}
    (->> (project-configuration/get-project-configuration tree-id)
         :jobs convert-to-array doall
         (some #(when (= (keyword (:key %)) (keyword job-key))%) )
         (#(or % (throw (ex-info (str "No job with the key '"
                                      job-key "' found.")
                                 {:status 404 :tree-id tree-id :job-key job-key})))))))

(defn persist-job [params tx]
  (->> (jdbc/insert!
         tx :jobs
         (select-keys params
                      [:tree_id, :job_specification_id, :created_by
                       :name, :description, :priority, :key, :user_id,
                       :trigger_event]))
       first))

(defn- on-job-exception [job ex tx]
  (jdbc/update!
    tx :jobs {:state "defective"}
    ["jobs.id = ?" (:id job)])
  (let [issue (merge
                {:title (.getMessage ex)
                 :type "error"
                 :description "Unspecified error, see the builder logs for details."
                 :job_id (:id job) }
                (or (ex-data ex) {}))]
    (jdbc/insert!
      tx :job_issues
      (select-keys issue [:id :job_id :title :description :type]))))

(defn create
  ([params]
   (jdbc/with-db-transaction [tx (rdbms/get-ds)]
     (create params tx)))
  ([params tx]
   (let [{tree-id :tree_id} params]
     (catcher/snatch
       {:return-fn (fn [ex]
                     (issues/create-issue "tree" tree-id ex)
                     (throw ex))}
       (let [{job-key :key} params
             normalized-spec (->> (raw-job-spec tree-id job-key)
                                  normalizer/normalize-job-spec
                                  (tasks-generator/generate tree-id))
             job-spec-row (spec/get-or-create-job-spec normalized-spec tx)
             job-params (merge
                          {:key job-key :name job-key}
                          (select-keys normalized-spec [:name, :description, :priority])
                          {:job_specification_id (:id job-spec-row)}
                          (select-keys params [:tree_id :priority :created_by :trigger_event]))
             job (persist-job job-params tx)]
         (catcher/snatch
           {:return-fn (fn [e] (on-job-exception job e tx))}
           (job-validator/validate! job (:data job-spec-row)))
         (tasks/create-tasks-and-trials job job-spec-row tx)
         job)))))


;### available jobs #####################################################

(defn available-jobs [tree-id]
  (->> (cider-ci.server.builder.project-configuration/get-project-configuration tree-id)
       :jobs
       convert-to-array
       (map #(assoc % :tree_id tree-id :runnable true :reasons []))
       jobs.dependencies/evaluate
       (map #(select-keys % [:name :key :tree_id :description :runnable :reasons]))))


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
