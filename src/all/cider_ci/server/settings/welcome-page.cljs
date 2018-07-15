; Copyright © 2017 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.settings.welcome-page
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.constants :as constants :refer [GRID-COLS]]
    [cider-ci.server.client.breadcrumbs :as breadcrumbs]
    [cider-ci.server.client.connection.request :as request]
    [cider-ci.server.client.constants :refer [CONTEXT]]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.shared :refer [pre-component]]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.settings.shared :as settings]
    [cider-ci.utils.core :refer [keyword str presence]]

    [markdown.core :refer [md->html]]
    [cljs.core.async :as async]
    [reagent.core :as reagent]
    [reagent.ratom :as ratom]
    ))



(def message-buffer* (ratom/atom nil))

(def mode?* (ratom/atom :show))

(defn enter-edit-mode []
  (reset! message-buffer* (-> @settings/settings* :welcome_page :message))
  (reset! mode?* :edit))

(def patch-id* (ratom/atom nil))

(defn patch []
  (let [resp-chan (async/chan)
        id (request/send-off {:url "/cider-ci/settings/welcome_page"
                              :method :patch
                              :json-params {:message (presence @message-buffer*)}}
                             {:title "Update Welcome-Page Message"
                              :autoremove-on-success true}
                             :chan resp-chan)]
    (reset! patch-id* id)
    (go (let [resp (<! resp-chan)]
          (when (and (= (:status resp) 204) ;success
                     (= id @patch-id*)) ;still the most recent request
            (settings/fetch)
            (reset! mode?* :show))))))

(defn page []
  [:div.settings-welcome-page
   (breadcrumbs/breadcrumb-component
     [(breadcrumbs/home-li-component)
      (settings/settings-breadcrumb-component :active? false)
      (settings/welcome-page-breadcrumb-component :active? true)]
     [])
   [:h1 "Welcome-Page Settings"]
   [:div.message
    [:h2 "Welcome-Page Message"]
    (case @mode?*
      :edit [:div.edit
             [:div.from-group
              [:label "Welcome Message ("
               [:a {:href "https://en.wikipedia.org/wiki/Markdown#Example"}
                "markdown" ] ")"]
              [:textarea.form-control
               {:style {:width "100%"}
                :value @message-buffer*
                :rows 7
                :on-change #(reset! message-buffer* (-> % .-target .-value presence))}]]
             [:div.form-group {:style {:margin-top "1em"}}
              [:button.btn.btn-warning.pull-left
               {:on-click #(reset! mode?* :show)}
               [:i.fa.fa-remove] " Cancel "]
              [:button.btn.btn-primary.pull-right
               {:on-click patch}
               [:i.fa.fa-send] " Save "]]]
      :show [:div.show
             [:pre (-> @settings/settings* :welcome_page :message)]
             [:button.btn.btn-primary.pull-right
              {:on-click enter-edit-mode}
              [:i.fa.fa-pencil] " Edit "]])
    [:div.clearfix]



    ]])
