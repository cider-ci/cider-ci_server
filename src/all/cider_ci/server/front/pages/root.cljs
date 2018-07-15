(ns cider-ci.server.front.pages.root
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.paths :as paths]
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]

    [cider-ci.utils.core :refer [keyword str presence]]
    ))

(defn page []
  [:div.root
   [:div.row
    [:nav.col-lg {:aria-label :breadcrumb :role :navigation}
     [:ol.breadcrumb
      [breadcrumbs/home-li]
      ]]
    [:nav.col-lg {:role :navigation}
     [:ol.breadcrumb.leihs-nav-right
      [breadcrumbs/admin-li]]]]
   [:h1 "Yadayadayada"]])
