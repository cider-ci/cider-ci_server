; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs
  (:require
    [cider-ci.builder.configfile]
    [cider-ci.builder.jobs.filter :as jobs.filter]
    [cider-ci.builder.jobs.tags :as tags]
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.tasks :as tasks]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.map :refer [deep-merge convert-to-array]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-logging-config.log4j :as logging-config]
    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [honeysql.core :as hc]
    [honeysql.helpers :as hh]
    ))


;### create job #########################################################

(defn- log-item [x]
  x)

(defn get-job-by-key [params configfile]
  (get (:jobs configfile) (keyword (:key params))))

(defn try-to-add-specification-from-configfile [params]
  (try
    (let [spec (->> (cider-ci.builder.configfile/get-configfile (:tree_id params))
                    (get-job-by-key params))]
      (logging/debug {:params params :job_specification spec})
      (conj  {:job_specification spec} params))
    (catch clojure.lang.ExceptionInfo e
      (case (-> e ex-data :object :status)
        404 params
        (throw e)))))

(defn add-specification-id-if-not-present [params]
  (if (:job_specification_id params)
    params
    (assoc params :job_specification_id
           (-> params
               :job_specification
               spec/get-or-create-job-spec
               :id))))

(defn add-specification-from-dofile-if-not-present [params]
  (if (and (not (:job_specification_id params))
           (not (:job_specification params)))
    (try-to-add-specification-from-configfile params)
    params))


(defn invoke-create-tasks-and-trials [params]
  (tasks/create-tasks-and-trials {:job_id (:id params)})
  params)

(defn persist-job [params]
  (->> (jdbc/insert!
         (rdbms/get-ds)
         :jobs
         (select-keys params
                      [:tree_id, :job_specification_id,
                       :name, :description, :priority, :key]))
       first
       (conj params)))

(defn create [params]
  (catcher/wrap-with-log-error
    (-> params
        add-specification-from-dofile-if-not-present
        add-specification-id-if-not-present
        persist-job
        invoke-create-tasks-and-trials
        tags/add-job-tags)))

;### available jobs #####################################################

(defn available-jobs [tree-id]
  (->> (cider-ci.builder.configfile/get-configfile tree-id)
       :jobs
       convert-to-array
       (map #(assoc % :tree_id tree-id))
       (filter #(jobs.filter/dependencies-fulfilled? tree-id %))
       (map #(select-keys % [:name :key :tree_id :description]))
       ))


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
