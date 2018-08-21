(ns cider-ci.server.front.state
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]])
  (:require
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.dom :as dom]

    [clojure.pprint :refer [pprint]]
    [reagent.core :as reagent]
    [timothypratley.patchin :as patchin]

    ))


;;; routing ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce routing-state* (reagent/atom {}))

(defn hidden-routing-state-component [handlers]
  "handlers is a map of keys to functions where the keys :will-mount,
  :did-mount, :did-update correspond to the react lifcycle methods.
  The custom :did-change will only fire when the routing state has changed,
  where as :did-update can fire when other reactive monitored state changes."
  (let [old-state* (reagent/atom nil)]
    (reagent/create-class
      {:component-will-mount (fn [& args] (when-let [handler (:will-mount handlers)]
                                            (apply handler args)))
       :component-will-unmount (fn [& args] (when-let [handler (:will-unmount handlers)]
                                            (apply handler args)))
       :component-did-mount (fn [& args] (when-let [handler (:did-mount handlers)]
                                           (apply handler args)))
       :component-did-update (fn [& args]
                               (when-let [handler (:did-update handlers)]
                                 (apply handler args))
                               (when-let [handler (:did-change handlers)]
                                 (let [old-state @old-state*
                                       new-state @routing-state*]
                                   (when (not= old-state new-state)
                                     (reset! old-state* new-state)
                                     (handler old-state (patchin/diff old-state new-state) new-state)))))
       :reagent-render
       (fn [_]
         [:div.hidden-routing-state-component
          {:style {:display :none}}
          [:pre (with-out-str (pprint @routing-state*))]])})))


;;; socket ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def socket* (reagent/atom
               {:msg_received_at (js/moment)
                :timestamped_at (js/moment)
                :msg_sent_at (js/moment)
                }))


(def socket-active?*
  (reaction
    (let [msg-received-at (:msg_received_at @socket*)
          msg-sent-at (:msg_sent_at @socket*)
          timestamped-at (:timestamped_at @socket*) ]
      (or (>= 1000 (Math/abs (- (.valueOf timestamped-at)
                                (.valueOf msg-received-at))))
          (>= 1000 (Math/abs (- (.valueOf timestamped-at)
                                (.valueOf msg-sent-at))))))))
(js/setInterval
  #(swap! socket*
          assoc :timestamped_at (js/moment))
  1000)


;;; ;;;;;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn hidden-state-component [handlers component-with-state]
	(reagent/create-class
		{:component-will-mount (fn [& args] (when-let [handler (:will-mount handlers)]
																					(apply handler args)))
		 :component-will-unmount (fn [& args] (when-let [handler (:will-unmount handlers)]
																						(apply handler args)))
		 :component-did-mount (fn [& args] (when-let [handler (:did-mount handlers)]
																				 (apply handler args)))
		 :component-did-update (fn [& args] (when-let [handler (:did-update handlers)]
																					(apply handler args)))
		 :reagent-render (fn [_]
											 [:div.hidden-state-component
												{:style {:display :none}}
												[component-with-state]
												])}))


;;; global ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce global-state* (reagent/atom {:debug false
                                      :users-query-params {}
                                      :timestamp (js/moment)}))

(js/setInterval #(swap! global-state*
                       (fn [s] (merge s {:timestamp (js/moment)}))) 1000)


;;; other ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def user* (atom (dom/data-attribute "body" "user")))

(def settings* (atom (dom/data-attribute "body" "settings")))

(def debug?* (reaction (:debug @global-state*)))

(defn update-state [state-ref key-seq fun]
  (swap! state-ref
         (fn [cs]
           (assoc-in cs key-seq
                     (fun (get-in cs key-seq nil))))))


;;; UI ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn debug-toggle-navbar-component []
  [:form.form-inline
   [:input#toggle-debug
    {:type :checkbox
     :checked (-> @global-state* :debug boolean)
     :on-click #(update-state global-state*
                               [:debug]
                               (fn [v] (not v)))}]
   [:label.navbar-text {:for "toggle-debug"
                        :style {:padding-left "0.25em"}} " debug "]])

(defn debug-component []
  (when (:debug @global-state*)
    [:div.debug
     [:hr]
     [:h2 "Debug State"]
     [:div
      [:h3 "@global-state*"]
      [:pre (with-out-str (pprint @global-state*))]]
     [:div
      [:h3 "@routing-state*"]
      [:pre (with-out-str (pprint @routing-state*))]]
     [:div
      [:h3 "@socket*"]
      [:pre (with-out-str (pprint @socket*))]]
     [:div
      [:h3 "@user*"]
      [:pre (with-out-str (pprint @user*))]]]))
