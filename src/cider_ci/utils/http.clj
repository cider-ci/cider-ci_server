(ns cider-ci.utils.http
  (:refer-clojure
    :exclude [get])
  (:require
    [cider-ci.utils.config :refer [get-config]]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.catcher :as catcher]
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    ))


(defonce conf (atom nil))

;### build url ##################################################################

(defn sanitize-query-params [params]
  (into {} (sort (for [[k v] params]
                   [(-> k name clojure.string/trim
                        clojure.string/lower-case
                        (clojure.string/replace " " "-")
                        (clojure.string/replace "_" "-")
                        (clojure.string/replace #"-+" "-")
                        keyword)
                    v]))))

(defn build-url-query-string [params]
  (-> params sanitize-query-params
      http-client/generate-query-string))

(defn build-url
  ([config path]
   (logging/warn "you probably should rather use build-service-url instead of build-url")
   (let [ protocol (cond
                     (= true (:server_ssl config)) "https"
                     (= true (:ssl config)) "https"
                     (= false (:server_ssl config)) "http"
                     (= false (:ssl config)) "http"
                     :else nil)
         host (or (:server_host config) (:host config))
         port (or (:server_port config) (:port config))
         context (:context config)
         sub-context (:sub_context config)
         ]
     (str (when protocol (str protocol "://" ))
          host
          (when port (str ":" port))
          context sub-context path)))
  ([config path query-params]
   (str (build-url config path)
        "?" (build-url-query-string query-params))))

;### build-service-url ########################################################

(defn get-service-http-config [service-name-or-keyword]
  (let [service-kw (keyword service-name-or-keyword)]
    (-> (get-config) :services service-kw :http)))

(defn build-service-prefix [service-name-or-keyword]
  (let [config (get-service-http-config service-name-or-keyword) ]
    (str (:context config) (:sub_context config))))

(defn build-service-path [service-name-or-keyword path]
  (str (build-service-prefix service-name-or-keyword) path))

(defn get-base-url []
  (:server_base_url (get-config)))

(defn build-server-url [path]
  (str (get-base-url) path))

(defn build-service-url
  ([service-name-or-keyword path]
   (str (get-base-url) (build-service-prefix service-name-or-keyword) path))
  ([service-name-or-keyword path query-params]
   (build-service-url service-name-or-keyword
                      (str path "?" (build-url-query-string query-params)))))

;### Http request #############################################################

(defn- request [method url params]
  (logging/debug [method url params])
  (let [basic-auth (:basic_auth @conf)]
    (catcher/wrap-with-log-error
      (logging/debug ("http/" method) {:url url :basic-auth basic-auth})
      (http-client/request
        (conj {:basic-auth [(:username basic-auth) (:password basic-auth)]
               :url url
               :method method
               :insecure? true
               :content-type :json
               :accept :json
               :socket-timeout 1000
               :conn-timeout 1000 }
              params)))))

(defn get [url params]
  (logging/debug get [url params])
  (request :get url params))

(defn post [url params]
  (logging/debug post [url params])
  (request :post url params))

(defn put [url params]
  (logging/debug put [url params])
  (request :put url params))

(defn patch [url params]
  (logging/debug patch [url params])
   (request :patch url params))


;### Initialize ###############################################################

(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


