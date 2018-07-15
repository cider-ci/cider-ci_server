; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.projects.repositories.project-configuration.expansion
  (:require
    [cider-ci.server.projects.repositories.project-configuration.replacement :as replacement]
    [cider-ci.server.projects.repositories.project-configuration.shared 
     :refer [get-content parse-path-content resolve-submodule-git-ref]]

    [cider-ci.utils.core :refer [deep-merge]]

    [clojure.data.json :as json]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    ))


(defn- get-include-content-for-path [git-ref-id path]
  (assert git-ref-id)
  (assert path)
  (let [raw-content (get-content git-ref-id path [])
        content (parse-path-content path raw-content)]
    (if-not (map? content)
      (throw (IllegalStateException.
               (str "Only maps can be included. Given "
                    (type content))))
      content)))


;##############################################################################

(defn format-include-spec [include-spec]
  (cond
    (= (type include-spec) java.lang.String) {:path include-spec
                                              :submodule []}
    (map? include-spec) {:submodule (or (:submodule include-spec) [])
                         :path (or (:path include-spec)
                                   (throw (IllegalStateException.
                                            (str "Can not determine :path for include-spec: "
                                                 include-spec))))}
    :else (throw (IllegalStateException. (str "include-spec must be either a map or string, is "
                                              (type include-spec))))))

(defn- format-include-specs [include-specs]
  (cond
    (= (type include-specs) java.lang.String) [(format-include-spec include-specs)]
    (coll? include-specs) (map format-include-spec include-specs)))


;##############################################################################

(declare expand)

(defn- get-inclusion [git-ref-id include-spec]
  (let [submodule-ref (resolve-submodule-git-ref [git-ref-id]
                                                 (or (:submodule include-spec) []))
        content  (get-include-content-for-path submodule-ref (:path include-spec))]
    (expand submodule-ref content)))

(defn get-inclusions [git-ref-id include-specs]
  (I>> identity-with-logging
       (format-include-specs include-specs)
       (map #(get-inclusion git-ref-id %))
       (reduce deep-merge)))

(defn include-maps [git-ref-id spec]
  (if-let [include-specs (:include spec)]
    (let [to-be-included (get-inclusions
                           git-ref-id include-specs)]
      (include-maps git-ref-id
                    (deep-merge
                      to-be-included
                      (dissoc spec :include))))
    (->> spec
         (map (fn [[k v]] [k (expand git-ref-id v)]))
         (into {}))))


;### expand ###################################################################

(defn expand [git-ref-id spec]
  (catcher/with-logging {}
    (cond
      (map? spec) (I>> identity-with-logging
                       spec
                       (include-maps git-ref-id)
                       (replacement/read-and-replace git-ref-id))
      (coll? spec) (->> spec
                        (map #(if (coll? %)
                                (expand git-ref-id %)
                                %)))
      :else spec)))


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
