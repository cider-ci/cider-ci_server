; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs
  (:require
    [cider-ci.builder.issues :as issues]
    [cider-ci.builder.jobs.dependencies :as jobs.dependencies]
    [cider-ci.builder.jobs.normalizer :as normalizer]
    [cider-ci.builder.jobs.tasks-generator :as tasks-generator]
    [cider-ci.builder.jobs.validator.job :as job-validator]

    [cider-ci.builder.project-configuration :as project-configuration]
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.tasks :as tasks]

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

(defn get-dotfile-specification [tree-id job-key]
  (catcher/with-logging {}
    (->> (project-configuration/get-project-configuration tree-id)
         debug/identity-with-logging
         :jobs
         debug/identity-with-logging
         convert-to-array
         doall
         debug/identity-with-logging
         (some #(when (= (keyword (:key %)) (keyword job-key))%) )
         debug/identity-with-logging
         (#(or % (throw (ex-info (str "No job with the key '"
                                      job-key "' found.")
                                 {:status 404 :tree-id tree-id :job-key job-key})))))))

;(apply get-dotfile-specification (debug/get-last-argument #'get-dotfile-specification))
;(debug/re-apply-last-argument #'get-dotfile-specification)
;(project-configuration/get-project-configuration "7c4f1e1efcdf9854927e1808ffa9182319227839" )

(defn persist-job [params]
  (->> (jdbc/insert!
         (rdbms/get-ds)
         :jobs
         (select-keys params
                      [:tree_id, :job_specification_id, :created_by
                       :name, :description, :priority, :key, :user_id]))
       first))

(defn on-job-exception [job ex]
  (jdbc/update!
    (rdbms/get-ds) :jobs {:state "defective"}
    ["jobs.id = ?" (:id job)])
  (let [issue (merge
                {:title (.getMessage ex)
                 :type "error"
                 :description "Unspecified error, see the builder logs for details."
                 :job_id (:id job) }
                (or (ex-data ex) {}))]
    (jdbc/insert!
      (rdbms/get-ds)
      :job_issues
      (select-keys issue [:id :job_id :title :description :type]))))

(defn create [params]
  (let [{tree-id :tree_id} params]
    (catcher/snatch
      {:return-fn (fn [ex]
                    (issues/create-issue "tree" tree-id ex)
                    (throw ex))}
      (let [{tree-id :tree_id job-key :key} params
            spec (->> (get-dotfile-specification tree-id job-key)
                     normalizer/normalize-job-spec
                     (tasks-generator/generate tree-id)
                     )
            job-spec (spec/get-or-create-job-spec spec)
            job-params (merge
                         {:key job-key :name job-key}
                         (select-keys spec [:name, :description, :priority])
                         {:job_specification_id (:id job-spec)}
                         (select-keys params [:tree_id :priority :created_by]))
            job (persist-job job-params)]
        (catcher/snatch
          {:return-fn (fn [e] (on-job-exception job e))}
          (job-validator/validate! job)
          (tasks/create-tasks-and-trials job job-spec))
        job))))


;### available jobs #####################################################

(defn available-jobs [tree-id]
  (->> (cider-ci.builder.project-configuration/get-project-configuration tree-id)
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
