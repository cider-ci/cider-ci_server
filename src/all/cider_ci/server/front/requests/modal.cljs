(ns cider-ci.server.front.requests.modal
  (:refer-clojure :exclude [str keyword ])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.front.state :as state]
    [cider-ci.server.front.requests.shared :as shared]
    [cider-ci.server.paths :as paths :refer [path]]

    [cider-ci.utils.core :refer [str keyword deep-merge presence]]
    ))

(def current-modal-request
  (do (reaction
        (when-not ((:handler-key @state/routing-state*) 
                   #{:request :requests})
          (when-let [[id r] (->> @shared/state* :requests
                                 (filter (fn [[_ r]] 
                                           (or (-> r :meta :modal)
                                               (and (-> r :response)
                                                    (-> r :response :success not)))))
                                 first)]
            (assoc r :id id))))))

(defn progress-bar-component [status bootstrap-status request]
  (let [progress-part (if-let [progress (-> request :progress)]
                        (if-let [total (-> progress :total)]
                          (max 0.1 (/ (:loaded progress) total))
                          0.1)
                        0.1)]
    [:div.progress
     [:div.progress-bar
      {:class (case status
                :pending "progress-bar-striped active"
                :success "progress-bar-success"
                :error "progress-bar-danger")
       :style {:width (str (* progress-part 100) "%")}}]]))

(defn modal-body [request status bootstrap-status]
  [:div.modal-body {:key (str "modal-body_" (:id request))}
   (when (= status :pending)
     [progress-bar-component status bootstrap-status request])
   (when-let [body (-> request :response :body presence)]
     [:div
      [:p "The message sent with the response says:"]
      [:pre body]])
   [:p [:a {:href (path :request {:id (:id request)})}
        "Inspect the details of this request."]]])

(defn modal-component []
  (when-let [request @current-modal-request]
    (let [status (shared/status request)
          bootstrap-status (shared/bootstrap-status status)]
      [:div {:style {:opacity (case status :pending "1.0" "1.0") :z-index 10000}}
       [:div.modal {:style {:display "block" :z-index 10000}}
        [:div.modal-dialog
         [:div.modal-content
          [:div.modal-header {:class (str "text-" bootstrap-status)}
           [:h4 (str " Request "
                     (when-let [title (-> request :meta :title)]
                       (str " \"" title "\" "))
                     (case status
                       :error " ERROR "
                       :success " OK "
                       :pending " PENDING "
                       nil))
            (-> request :response :status)]]
          [modal-body request status bootstrap-status]
          [:div.modal-footer {:key (str "modal-footer_" (:id request))}
           [:div.clearfix]
           [shared/dismiss-button-component request]
           (when-let [retry-fn (and (= status :error)
                                    (-> request :meta :retry-fn))]
             [:button.btn.btn-primary
              {:on-click  (fn [_]
                            (swap! shared/state*
                                   update-in [:requests]
                                   (fn [rx] (dissoc rx (:id request))))
                            (retry-fn))}
              [:i.fa.fa-recycle] " Resend request "])]
          ]]]
       [:div.modal-backdrop {:style {:opacity "0.5"}}]])))


