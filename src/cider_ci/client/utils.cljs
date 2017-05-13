(ns cider-ci.client.utils
  (:require
    [fipp.edn :refer [pprint]]
    [cider-ci.client.state :as state]
    ))

(defn humanize-datetime [ref_dt dt]
  (.to (js/moment) dt))

(defn humanize-datetime-component [dt]
  (if-let [dt (if (string? dt) (js/moment dt) dt)]
    [:span.datetime
     {:data-iso8601 (.format dt)}
     ;[:pre (with-out-str (pprint dt))]
     (humanize-datetime (:timestamp @state/client-state) dt)]
    [:span "NULL"]))
