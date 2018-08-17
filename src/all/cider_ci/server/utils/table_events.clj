(ns cider-ci.server.utils.table-events
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.utils.core :refer [str keyword presence]]
    [cider-ci.utils.honeysql :as sql]
    [cider-ci.utils.rdbms :as ds]
    [cider-ci.utils.daemon :refer [defdaemon]]

    [clj-time.core :as time]
    [clojure.core.async :as async]
    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))

(def ^:private pub-chan (async/chan))
(def ^:private pub (async/pub pub-chan :table_name))

(defn- publish [row]
  (logging/info 'publish row)
  (async/>!! pub-chan row)
  row)

;(publish {:table_name "projects" :foo :baz})
;(async/>!! pub-chan {:table_name :test})

;(dotimes [i 20] (async/>!! pub-chan {:table-name :test :event {:message "blah" :i i :time (time/now)}}))

(defn subscribe [chan table-name]
  ;TODO the following fails with sliding-buffer, but it should not 
  ;(assert (async/unblocking-buffer? chan))
  (async/sub pub table-name chan)
  chan)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce last-event-row* (atom nil))
;(reset! last-event-row* nil)

(def base-query
  (-> (sql/select :*)
      (sql/from :events)
      (sql/order-by [:created_at :asc]
                    [:id :asc])))

(defn set-last-event-row []
  (swap! last-event-row*
         (fn [last-event-row]
           (or last-event-row
               (->> (-> base-query  
                        (sql/order-by [:created_at :desc])
                        (sql/limit 1)
                        (sql/format))
                    (jdbc/query @ds/ds)
                    first)))))

(defn extend-query [query last-event-row]
  (if-not last-event-row
    query
    (-> query
        (sql/merge-where [:> :created_at 
                          (-> (sql/select :created_at)
                              (sql/from :events)
                              (sql/merge-where [:= :id (:id last-event-row)]))]))))
                                             
                                             

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
                    doall
                    last)
               last-event-row))))



;(publish-new-rows!)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; testing
(def sub-chan (async/chan (async/sliding-buffer 10)))
(subscribe sub-chan "projects")
(async/go-loop [] (let [msg (async/<! sub-chan)] (println msg) (logging/info msg) (recur)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn init []
  (logging/info 'init "table-events")
  (set-last-event-row)
  (defdaemon "publish-new-rows" 0.1 (publish-new-rows!))
  (start-publish-new-rows))

(defn de-init []
  (stop-publish-new-rows))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
(logging-config/set-logger! :level :debug)
