(ns cider-ci.repository.ui.projects.shared
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.repository.ui.request :as request]

    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.client.state :as state]

    [cider-ci.utils.url]

    [accountant.core :as accountant]
    [cljs.pprint :refer [pprint]]
    [reagent.core :as r]))


(defn humanize-datetime [_ dt]
  (.to (js/moment) dt))


