(ns cider-ci.server.client.breadcrumbs
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.constants :as constants :refer [GRID-COLS]]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.shared :refer [pre-component]]
    [cider-ci.server.client.state :as state]
    [cider-ci.utils.core :refer [keyword str presence]]

    [reagent.core :as reagent]
    [reagent.ratom :as ratom]
    ))

(defn breadcrumb-component [left right]
  [:div.row
   [:div
    {:class (str "col-md-" (Math/floor (/ GRID-COLS 2)))}
    [:ol.breadcrumb (for [i left] i) ]]
   [:div
    {:class (str "col-md-" (Math/floor (/ GRID-COLS 2)))}
    (when-not (empty? right)
      [:ol.breadcrumb.with-circle-separator (for [i right] i)])]])

(defn home-li-component
  [& {:keys [active?] :or {:active? false}}]
  [:li {:key :home
        :class (when active? "active")}
   (let [internal [:span [:i.fa.fa-home] " Home "]]
     (if-not active?
       [:a {:href "/cider-ci/" } internal]
       internal))])

