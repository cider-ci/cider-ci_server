(ns cider-ci.repository.web.edn
  (:require
    [clj-time.format :as f]
    [clj-time.core :as time]
    )
  (:import [org.joda.time DateTime LocalDate]))

;;
;; #DateTime tagging
;;
(def iso8601-formatter (f/formatters :date-time))
(def date-formatter (f/formatters :date))

(defn- datetime->reader-str [d]
  (str "\"" (f/unparse iso8601-formatter d) \"))

(defn- date->reader-str [d]
  (str "\"" (f/unparse-local-date date-formatter d) \"))

(defn- reader-str->datetime [s]
  (f/parse iso8601-formatter s))

(defn- reader-str->date [s]
  (f/parse-local-date date-formatter s))

(do
  (defmethod print-dup DateTime [^DateTime d out]
    (.write out (datetime->reader-str d)))

  (defmethod print-method DateTime [^DateTime d out]
    (.write out (datetime->reader-str d)))

  (defmethod print-dup LocalDate [^LocalDate d out]
    (.write out (date->reader-str d)))

  (defmethod print-method LocalDate [^LocalDate d out]
    (.write out (date->reader-str d)))

  ;(alter-var-root #'*data-readers* assoc
  ;                'DateTime #'cider-ci.repository.web.edn/reader-str->datetime
  ;                'Date #'cider-ci.repository.web.edn/reader-str->date)

  )


;(clojure.edn/read-string (prn-str (str (time/now))))
;(clojure.edn/read-string (prn-str (time/now)))


