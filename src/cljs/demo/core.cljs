(ns demo.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [demo.events :as events]
            [demo.db :as db]
            [demo.views :as views]
            [demo.config :as config]))


(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (println "dev mode")))

(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (re-frame/dispatch-sync [::db/initialize-db])
  (dev-setup)
  (mount-root))
