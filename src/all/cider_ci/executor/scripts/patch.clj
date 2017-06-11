(ns cider-ci.executor.scripts.patch
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.executor.reporter :as reporter]
    [cider-ci.utils.http :refer [build-server-url]]
    [clojure.data.json :as json]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
  ))

(defonce ^:private patch-agents-atom (atom {}))

(defn- patch-url [trial-params script-params]
  (str (build-server-url (:patch_path trial-params))
       "/scripts/" (clj-http.util/url-encode (:key script-params))))

(defn create-patch-agent [script-params trial-params]
  (let [swap-in-fun (fn [patch-agents]
                      (assoc patch-agents
                             (:id script-params)
                             {:agent (agent { :url (patch-url trial-params script-params)
                                             :token (:token trial-params)
                                             } :error-mode :continue)}))]
    (swap! patch-agents-atom swap-in-fun)))

(defn- get-patch-agent-data [trial-params]
  (get @patch-agents-atom (:id trial-params)))


;##############################################################################

(defn- send-patch [agent-state data]
  (catcher/snatch
    {:return-fn (fn [e] (merge agent-state {:last-exception e}))}
    (let [{url :url token :token} agent-state
          body (json/write-str data)
          params {:body body
                  :headers {:trial-token token}
                  :content-type "application/json"}]
      (let [res (reporter/send-request-with-retries :patch url params)]
        (merge agent-state {:last-patch-result res})))))

(defn- send-patch-via-agent [script-atom new-state]
  (let [{patch-agent :agent} (get-patch-agent-data @script-atom)
        fun (fn [agent-state] (send-patch agent-state new-state))]
    (send-off patch-agent fun)))

(defn send-field-patch [agent-state field value]
  (catcher/snatch
    {:return-fn (fn [e] (merge agent-state {:last-exception e}))}
    (let [{base-url :url token :token} agent-state
          url (str base-url "/" (name field))
          params {:body value
                  :headers {:trial-token token}
                  :content-type "text/plain"}]
      (let [res (reporter/send-request-with-retries :patch url params)]
        (merge agent-state {:last-patch-result res})))))

(defn send-field-patch-via-agent [script-atom field value]
  (let [{patch-agent :agent} (get-patch-agent-data @script-atom)
        fun (fn [agent-state] (send-field-patch agent-state field value))]
    (send-off patch-agent fun)))


;##############################################################################

(defn- watch [key script-atom old-state new-state]
  (logging/debug :NEW-SCRIPT-STATE (json/write-str {:old-state old-state :new-state new-state}))
  (send-patch-via-agent script-atom new-state))

(defn add-watcher [script-atom]
  (add-watch script-atom :watch watch))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
