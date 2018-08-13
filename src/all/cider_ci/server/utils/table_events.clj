(ns cider-ci.server.utils.table-events
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.utils.core :refer [str keyword presence]]
    [cider-ci.utils.honeysql :as sql]
    [cider-ci.utils.rdbms :as ds]

    [clj-time.core :as time]
    [clojure.core.async :as async]
    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))

(def ^:private pub-chan (async/chan))
(def ^:private pub (async/pub pub-chan :table-name))

(defn- publish [row]
  (async/>!! pub-chan {:table-name (:table_name row)
                       :data row})
  row)


;(dotimes [i 20] (async/>!! pub-chan {:table-name :test :event {:message "blah" :i i :time (time/now)}}))

(defn subscribe [chan table-name]
  ;TODO the following fails with sliding-buffer, but it should not 
  ;(assert (async/unblocking-buffer? chan))
  (async/sub pub table-name chan))


;;;

(def last-event-row* (atom nil))

(def base-query
  (-> (sql/select :*)
      (sql/from :events)
      (sql/order-by [:created_at :asc]
                    [:id :asc])))

(defn extend-query [query last-event-row]
  (if-not last-event-row
    query
    (-> query
        (sql/merge-where [:>= :created_at (:created_at last-event-row)])
        (sql/merge-where [:<> :id (:id last-event-row)]))))

(defn build-publish-new-rows-query [last-event-row]
  (-> base-query  
      (extend-query last-event-row)
      (sql/format))) 

(defn publish-new-rows! []
  (swap! last-event-row*
         (fn [last-event-row]
           (or (->> (build-publish-new-rows-query last-event-row)
                    (jdbc/query @ds/ds)
                    (map publish)
                    last)
               last-event-row))))

;(publish-new-rows!)

(defn init []
  (swap! last-event-row*
         (fn [last-event-row]
           (->> (-> base-query  
                    (sql/format))
                (jdbc/query @ds/ds)
                first))))

; testing
(def sub-chan (async/chan (async/sliding-buffer 10)))
(subscribe sub-chan "projects")
(async/go-loop [] (let [msg (async/<! sub-chan)] (println msg) (logging/debug msg) (recur)))



;#### debug ###################################################################
(debug/debug-ns *ns*)
