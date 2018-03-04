(ns demo.views
  (:require [re-frame.core :as re-frame]
            [demo.db :as db]
            [demo.subs :as subs]))


(defn main-panel []
  (let [remote-success (re-frame/subscribe [::db/remote-success])
        remote-failure (re-frame/subscribe [::db/remote-failure])
        validation-failure (re-frame/subscribe [::db/validation-failure])
        remote-response (re-frame/subscribe [::db/remote-response])
        name (re-frame/subscribe [::db/name])]
    [:div
     [:h4 @name]
     [:input {:on-change #(re-frame/dispatch [::db/create-todo (.-value (.-target %))])}]
     (when @remote-success
       [:p {:style {:color "green"}} "Remote success!"])
     (when @remote-failure
       [:p {:style {:color "red"}} "Remote failure!"])
     (when @validation-failure
       [:p {:style {:color "red"}} "Validation failure!"])
     (when-let [remote-response @remote-response]
       [:p "Remote Response: " remote-response])]))
