; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.executors.front.add
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]])
  (:require
    [cider-ci.server.resources.api-token.front :as api-token]
    [cider-ci.server.front.requests.core :as requests]
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]
    [cider-ci.server.executors.front.breadcrumbs :as executor-breadcrumbs]
    [cider-ci.server.front.state :as state :refer [routing-state*]]
    [cider-ci.server.paths :as paths :refer [path]]
    [cider-ci.server.resources.auth.front :as auth]
    [cider-ci.server.front.shared :as shared :refer [humanize-datetime-component name->key]]

    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.seq :refer [with-index]]

    [accountant.core :as accountant]
    [cljs.pprint :refer [pprint]]
    [cljs.core.async :as async]
    [cljs.core.async :refer [timeout]]
    [reagent.core :as reagent]

    ))


(defonce executor-data* (reagent/atom {:enabled true}))

(defn post []
	(let [resp-chan (async/chan)
        data @executor-data*
				p1 {:url (path :executor {:executor-id (:id data)})
						:method :post
						:json-params data}
				_ (js/console.log (with-out-str (pprint p1)))
				id (requests/send-off 
						 p1 {:modal true :title "Post executor data"
								 :retry-fn post}
						 :chan resp-chan)]
		(go (let [resp (<! resp-chan)]
					(when (< (:status resp) 400)
						(accountant/navigate! 
							(path :executor {:executor-id (-> resp :body :id)})))))))

(defn form-name-component []
  [:div.form-group
   [:label  {:for :name} "Name"]
   [:input#name.form-control
    {:type :text
     :value (-> @executor-data* :name)
     :on-change #(swap! executor-data* assoc 
                        :name (-> % .-target .-value)
                        :id (-> % .-target .-value name->key))}]])

(defn form-id-component []
  [:div.form-group
   [:label  {:for :id} "Id"]
   [:input#id.form-control
    {:type :text
     :value (-> @executor-data* :id)
     :on-change #(swap! executor-data* assoc :id (-> % .-target .-value))}]
   [:small.form-text 
    "The id of a executor is used in URLs and and in other places for reference. " 
    "The id must begin with an lower case letter. "
    "Further only lower case letters, numbers, dashes and underscores are allowed. "
    "The id can not be changed after the executor has been added. " ]])

(defn checkbox-input-component [id ratom keyseq]
  [:input
   {:id id
    :type :checkbox
    :checked (get-in @ratom keyseq false)
    :on-change #(swap! ratom (fn [d]
                               (assoc-in d keyseq
                                         (not (get-in d keyseq false)))))}])

(defn form-enabled-component []
  [:div.form-group
   [:div.checkbox
    [:label
     [checkbox-input-component :enabled
      executor-data* [:enabled]]
     " enabled "]]
   [:small.form-text 
    "Uncheck to disable this executor and silence warnings too." ]]) 

(defn form-description []
  [:div.form-group
   [:label  {:for :id} "Description"]
   [:textarea#description.form-control
    {:type :text
     :value (-> @executor-data* :description)
     :on-change #(swap! executor-data* assoc :description (-> % .-target .-value presence))}]])

(defn form-public-key []
  [:div.form-group
   [:label  {:for :id} "Public Key"]
   [:textarea#public-key.form-control
    {:type :text
     :value (-> @executor-data* :public_key)
     :on-change #(swap! executor-data* assoc :public_key (-> % .-target .-value presence))}]])

(defn form-component []
  [:form.form
   {:on-submit (fn [e]
                 (.preventDefault e)
                 (post))}
   [form-name-component]
   [form-description]
   [form-id-component]
   [form-enabled-component]
   [form-public-key]
   [:button.btn.btn-primary.float-right
    {:type :submit}
    "Add Executor"]
   [:div.clearfix]]) 

(defn debug-component []
  (when (:debug @state/global-state*)
    [:div
     [:h3 "Page debug"]
     [:pre (with-out-str (pprint @executor-data*))]]))

(defn page []
  [:div.executors-page
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (executor-breadcrumbs/executors-li)
      (executor-breadcrumbs/executors-add-li)]
     [])
   [:h1 " Add a new Executor "]
   [form-component]
   [debug-component]
   ])
