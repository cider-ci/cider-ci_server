(ns cider-ci.server.users.api-tokens.ui.shared
  (:refer-clojure :exclude [str keyword])
  (:require-macros [reagent.ratom :as ratom :refer [reaction]])
  (:require
    [cider-ci.server.client.state :as state]
    [cider-ci.server.client.connection.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.utils.core :refer [keyword str presence]]
    ))

(def user-id* (reaction (-> @state/page-state :current-page :user-id presence)))

(def api-token-id*
  (reaction
    (-> @state/page-state :current-page :api-token-id presence)))

(def scopes [:scope_read :scope_write :scope_admin_read :scope_admin_write])

(def timestamps [:expires_at :created_at :updated_at])

(defn api-token-read [raw] raw )

(defn reload-tokens [& {:keys [callback]}]
  (let [user-id (-> @state/page-state :current-page :user-id)]
    (request/send-off
      {:url (routes/user-api-tokens-path
              {:user-id user-id})
       :method :get}
      {:title "Fetch API-Tokens"
       :autoremove-delay 0}
      :callback (fn [resp]
                  (swap! state/client-state
                         assoc-in [:users (keyword user-id) :api-tokens]
                         (->> resp :body :api-tokens
                              (map (fn [[k t]] [k (api-token-read t)]))
                              (into {})))
                  (when callback
                    (callback resp))))))

(defn boolean-value-component [b & {:keys [class] :or {class ""}}]
  [:span
   [:input {:class (str class)
            :type :checkbox
            :read-only true
            :checked b}]
   [:span " "]
   [:span.value {:class class} (str b)]])


