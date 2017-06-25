(ns cider-ci.server.repository.ui.projects.orientation
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.server.repository.constants :refer [CONTEXT]]
    [cider-ci.server.repository.ui.request :as request]
    [cider-ci.server.client.state :as state]

    [cider-ci.utils.url]

    [accountant.core :as accountant]
    [cljs.pprint :refer [pprint]]
    [reagent.core :as r]))


(defn breadcrumbs [params]
  (fn [params]
    (let [{id :id issue-key :issue-key} params]
      [:ol.breadcrumb
       [:li [:a {:href "/cider-ci/ui/public"} "Home"]]
       [:li [:a {:href (str CONTEXT "/projects/")} "Projects"]]
       (when id
         [:li [:a {:href (str CONTEXT "/projects/" id)} "Project"]])
       (when (and id issue-key)
         [:li [:a {:href (str CONTEXT "/projects/" id "/issues/" issue-key)} "Issue"]])
       ])))

