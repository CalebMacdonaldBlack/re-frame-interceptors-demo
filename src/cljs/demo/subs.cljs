(ns demo.subs
  (:require
    [re-frame.core :as re-frame]
    [demo.db :as db]))

(re-frame/reg-sub
 ::name
 (fn [db]
   (:name db)))

(re-frame/reg-sub
  ::db/remote-failure
  (fn [db]
    (:remote-failure db)))

(re-frame/reg-sub
  ::db/remote-success
  (fn [db]
    (:remote-success db)))

(re-frame/reg-sub
  ::db/validation-failure
  (fn [db]
    (:validation-failure db)))

(re-frame/reg-sub
  ::db/remote-response
  (fn [db]
    (:response db)))
