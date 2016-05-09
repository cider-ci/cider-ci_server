(ns cider-ci.builder.jobs.validator.shared
  (:require
    [cider-ci.utils.core :refer :all]

    [cider-ci.utils.duration :refer [parse-string-to-seconds]]

    [clojure.set :refer :all]

    ;[cider-ci.builder.ValidationException]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    )

  (:import
    [cider_ci.builder ValidationException]
    ))


;### helpers ###################################################################

(defn format-coll [coll]
  (->> coll
       (map (fn [k] (str " _\"" (to-cistr k) "\"_")))
       (clojure.string/join ", ")))

(defn format-chain [chain]
  (->> chain
       (map (fn [k] (str " _\"" (to-cistr k) "\"_")))
       (clojure.string/join " → ")))

(defn build-map-of-validator [validator]
  (fn [mp chain]
    (when-not (map? mp)
      (->> {:type "error"
            :description
            (str "The item in " (format-chain chain)
                 " must by a map, but it is " (type mp))}
           (ValidationException. "Type Mismatch")
           throw))
    (doseq [[k v] mp]
      (apply validator [v (conj chain k)]))))


;### primitive value validators ################################################

(defn validate-boolean!  [value chain]
  (when-not (instance? Boolean value)
    (->> {:type "error"
          :description (str "The value in " (format-chain chain)
                            " must be a boolean but it is a \"" (type value)\"".")}
         (ValidationException. "Type Mismatch")
         throw)))

(defn validate-integer!  [value chain]
  (when-not (integer? value)
    (->> {:type "error"
          :description (str "The value in " (format-chain chain)
                            " must be an integer but it is a \"" (type value)\"".")}
         (ValidationException. "Type Mismatch")
         throw)))

(defn validate-string!  [value chain]
  (when-not (instance? String value)
    (->> {:type "error"
          :description (str "The value in " (format-chain chain)
                            " must be a string but it is a \"" (type value)\"".")}
         (ValidationException. "Type Mismatch")
         throw)))

(defn validate-duration!
  [value chain]
  (validate-string! value chain)
  (try
    (assert
      (instance? Double (parse-string-to-seconds value))
      "The result of parse-string-to-seconds must be a Double!")
    (catch Exception e
      (->> {:type "error"
            :description (str "The value _\"" value "\"_ in " (format-chain chain)
                              " must be a string representing a duration."
                              " "
                              "Parsing the duration failed with an _\"" (.getMessage e)
                              "\"_ exception."
                              )}
           (ValidationException. "Invalid Duration String")
           throw))))


;### spec validators ###########################################################

(defn validate-accepted-keys! [test-spec meta-spec chain]
  (let [diff (difference (-> test-spec keys set)
                         (-> meta-spec keys set))]
    (when-not (empty? diff)
      (->> {:type "error"
            :description
            (str "The item in " (format-chain chain)
                 " includes the unknown property"
                 " _\"" (-> diff first to-cistr) "\"_.")}
           (ValidationException. "Unknown Property")
           throw))))

(defn validate-required-keys! [test-spec meta-spec chain]
  (let [diff (difference (->> meta-spec
                              (filter (fn ([[k v]] (:required v))))
                              (into {}) keys set)
                         (-> test-spec keys set))]
    (when-not (empty? diff)
      (->> {:type "error"
            :description
            (str "The item in " (format-chain chain)
                 " does not include the required property"
                 " _\"" (-> diff first to-cistr) "\"_.")}
           (ValidationException. "Required Property Missing")
           throw))))

(defn validate-values! [test-spec meta-spec chain]
  (doseq [[k s] meta-spec]
    (logging/debug [k s])
    (when (contains? test-spec k)
      (when-let [validator (:validator s)]
        (apply validator [(get test-spec k) (conj chain k)])))))

(defn validate-all! [test-specs meta-test-specs chain]
  (validate-accepted-keys! test-specs meta-test-specs chain)
  (validate-values! test-specs meta-test-specs chain)
  (validate-required-keys! test-specs meta-test-specs chain))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
