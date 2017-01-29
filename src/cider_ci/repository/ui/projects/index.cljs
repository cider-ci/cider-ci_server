(ns cider-ci.repository.ui.projects.index
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]

    )
  (:require
    ;[cider-ci.repository.ui.projects.orientation :as orientation]
    [cider-ci.repository.ui.projects.table :as table]
    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.client.state :as state]

    [cider-ci.utils.url]

    [secretary.core :as secretary :include-macros true]
    [cljs-http.client :as http]
    [cljs.core.async :refer [<!]]
    [cljs.pprint :refer [pprint]]
    ))

(declare page)

(secretary/defroute projects-path "/cider-ci/repositories/projects/" [query-params]
  (swap! state/page-state assoc :current-page
         {:component #'page
          :query-params query-params})
  (swap! state/client-state assoc :server-requests
         {:projects true}))

(defn page []
  [:div.projects
   [:div.row.orientation
    ;[:div.col-md-6 [orientation/breadcrumbs {}]]]
    [:div
     [:ul.actions.list-inline.pull-right
      [:li
       [:a.btn.btn-primary
        {:href (str CONTEXT "/projects/new")}
        [:i.fa.fa-plus-circle] " Add a new project"]]]
     [:h2 "Projects"]
     [:p "This page is is mainly concerned with the "
      [:b " connection "] "of your projects to their " [:b "origin"] "."
      "See the " [:b [:a {:href "/cider-ci/ui/workspace"} " workspace " ]] "page to inspect "
      [:b "commits" ] " and " [:b " jobs."]]]
    [table/table]
    ]])

