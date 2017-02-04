; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.app
  (:require
    [cider-ci.utils.config :as config :refer [get-config get-db-spec]]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.json-protocol]
    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.self :as self]

    [logbug.catcher :as catcher]
    [clojure.tools.logging :as logging]

    ))

(defn init [service-name build-http-handler-fn]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn (fn [e] (System/exit -1))}
    (assert (keyword? service-name))


    (logging/info (self/application-str))

    (logbug.thrown/reset-ns-filter-regex #".*cider.ci.*")

    (config/initialize {:overrides {:basic_auth {:username service-name}
                                    :service service-name}})

    (if-let [db-spec (get-db-spec service-name)]
      (rdbms/initialize db-spec)
      (logging/info (str "No database configuration found, "
                         "skipping database initialization.")))

    (if-let [nrepl-spec  (-> (get-config) :services service-name :nrepl)]
      (nrepl/initialize nrepl-spec)
      (logging/info (str "No nrepl configuration found, "
                         "skipping nrepl initialization.")))

    (if-not build-http-handler-fn
      (logging/info (str "No build-http-handler-fn given, "
                         "skipping http-server initialization."))
      (if-let [http-conf (-> (get-config) :services service-name :http)]
        (let [context (str (:context http-conf) (:sub_context http-conf))]
          (http-server/start http-conf (build-http-handler-fn context)))
        (logging/info (str "No http-config given, "
                           "skipping http-server initialization."))))))

