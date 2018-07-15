(ns cider-ci.server.front.pages.debug
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.paths :as paths]
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]

    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.url.query-params :as query-params]

    [clojure.pprint :refer [pprint]]
    ))


(defn page []
  [:div.debug

   [:div.row
    [:nav.col-lg {:aria-label :breadcrumb :role :navigation}
     [:ol.breadcrumb
      [breadcrumbs/home-li]
      [breadcrumbs/admin-li]
      [breadcrumbs/debug-li]]]
    [:nav.col-lg {:role :navigation}
     [:ol.breadcrumb.leihs-nav-right
      [breadcrumbs/requests-li]]]]


   [:h1 "Debug"]
   [:pre
    (with-out-str
      (pprint
        (.parse js/JSON
                (->  js/document .-body .-dataset .-user))
        ))]])
