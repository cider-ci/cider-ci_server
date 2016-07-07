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

(defn format-type-error [supposed-value-type actual-value chain]
  (->> ["The item in " (format-chain chain)
        " must by a \"" supposed-value-type "\","
        " but it is a \"" (type actual-value) "\"."]
       (map str)
       (clojure.string/join " ")))

(defn build-map-of-validator [validator]
  (fn [mp chain]
    (when-not (map? mp)
      (->> {:type "error"
            :description (format-type-error "map" mp chain)}
           (ValidationException. "Type Mismatch")
           throw))
    (doseq [[k v] mp]
      (apply validator [v (conj chain k)]))))

(defn build-collection-of-validator [validator]
  (fn [coll chain]
    (when (or (not (coll? coll))
              (map? coll))
      (->> {:type "error"
            :description
            (->> [(format-type-error "collection" coll chain)]
                 (map str)
                 (clojure.string/join "\n"))}
           (ValidationException. "Type Mismatch")
           throw))
    (doseq [v coll]
      (apply validator [v  chain]))))


;### primitive value validators ################################################

(defn validate-map! [value chain]
  (when-not (map? value)
    (->> {:type "error"
          :description (format-type-error "map" value chain)}
         (ValidationException. "Type Mismatch")
         throw)))

(defn validate-boolean!  [value chain]
  (when-not (instance? Boolean value)
    (->> {:type "error"
          :description (format-type-error "boolean" value chain)}
         (ValidationException. "Type Mismatch")
         throw)))

(defn validate-integer!  [value chain]
  (when-not (integer? value)
    (->> {:type "error"
          :description (format-type-error "integer" value chain)}
         (ValidationException. "Type Mismatch")
         throw)))

(defn validate-string!  [value chain]
  (when-not (instance? String value)
    (->> {:type "error"
          :description (format-type-error "string" value chain)}
         (ValidationException. "Type Mismatch")
         throw)))

;### shared validators #########################################################

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

(defn validate-states! [test-states valid-states chain]
  (when-not (every? valid-states test-states)
    (->> {:type "error"
          :description
          (str (-> test-states sort format-coll) " in " (format-chain chain)
               " contains an illegal state. Valid states are: "
               (-> valid-states sort format-coll) "."
               )}
         (ValidationException. "Illegal State")
         throw)))


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

(defn validate-spec-map! [test-specs meta-test-specs chain]
  (validate-map! test-specs chain)
  (validate-accepted-keys! test-specs meta-test-specs chain)
  (validate-values! test-specs meta-test-specs chain)
  (validate-required-keys! test-specs meta-test-specs chain))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
