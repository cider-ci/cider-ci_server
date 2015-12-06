; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs
  (:require
    [cider-ci.builder.jobs.normalizer :as normalizer]
    [cider-ci.builder.project-configuration :as project-configuration]
    [cider-ci.builder.jobs.dependencies :as jobs.dependencies]
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.tasks :as tasks]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.map :refer [deep-merge convert-to-array]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [clojure.tools.logging :as logging]
    ))


;### create job #########################################################

(defn get-dotfile-specification [tree-id job-key]
  (catcher/wrap-with-log-warn
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


(defn find-or-create-specifiation [spec]
  (spec/get-or-create-job-spec spec))

(defn invoke-create-tasks-and-trials [job]
  (tasks/create-tasks-and-trials {:job_id (:id job)})
  job)

(defn persist-job [params]
  (->> (jdbc/insert!
         (rdbms/get-ds)
         :jobs
         (select-keys params
                      [:tree_id, :job_specification_id, :created_by
                       :name, :description, :priority, :key, :user_id]))
       first))


(defn create [params]
  (catcher/wrap-with-log-error
    (let [{tree-id :tree_id job-key :key} params
          spec (-> (get-dotfile-specification tree-id job-key)
                   normalizer/normalize-job-spec )
          spec-id (-> spec find-or-create-specifiation :id)
          job-params (merge
                       {:key job-key :name job-key}
                       (select-keys spec [:name, :description, :priority])
                       {:job_specification_id spec-id}
                       (select-keys params [:tree_id :priority :created_by]))
          job (persist-job job-params)]
      (invoke-create-tasks-and-trials job))))


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
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'create)

