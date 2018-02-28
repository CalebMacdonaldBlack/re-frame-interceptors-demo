(ns demo.events
  (:require [re-frame.core :as re-frame]
            [demo.db :as db]))

;=============== Helper ===============

(defn ->before-interceptor [fn]
  {:id     :before-interceptor
   :before fn
   :after  identity})

(defn- valid-todo? [todo]
  (> (count todo) 10))

(re-frame/reg-event-db
  ::db/initialize-db
  (fn [_ _]
    db/default-db))

;=============== Chain ===============

(def chain
  (->before-interceptor
    (fn [{{[_ event interceptors response] :event} :coeffects :as ctx}]
      (-> ctx
          (update :queue concat interceptors)
          (update :queue vec)
          (assoc-in [:coeffects :event] (conj event response))))))

(re-frame/reg-event-ctx
  ::db/chain
  [chain]
  identity)

(def begin-chain-interceptor
  (->before-interceptor
    (fn [{{:keys [:db]} :coeffects :as ctx}]
      (assoc-in ctx [:effects :db] db))))

;=============== FX ===============

(re-frame/reg-fx
  ::db/remote-store
  (fn [{:keys [:method :url :todo :on-success :on-failure]}]
    (if (re-find #"success" todo)
      (re-frame/dispatch (conj on-success "id-123"))
      (re-frame/dispatch (conj on-failure "Connection error")))))

;=============== Interceptors ===============

(def remove-remote-success-interceptor
  (->before-interceptor
    #(assoc-in % [:effects :db :remote-success] false)))

(def remove-remote-failure-interceptor
  (->before-interceptor
    #(assoc-in % [:effects :db :remote-failure] false)))

(def remove-validation-failure-interceptor
  (->before-interceptor
    #(assoc-in % [:effects :db :validation-failure] false)))

(def add-remote-success-interceptor
  (->before-interceptor
    #(assoc-in % [:effects :db :remote-success] true)))

(def add-remote-failure-interceptor
  (->before-interceptor
    #(assoc-in % [:effects :db :remote-failure] true)))

(def add-validation-failure-interceptor
  (->before-interceptor
    #(assoc-in % [:effects :db :validation-failure] true)))

(def db-store-interceptor
  (->before-interceptor
    (fn [{{[_ todo] :event} :coeffects :as ctx}]
      (assoc-in ctx [:effects :db :todo] todo))))

(def db-store-response-interceptor
  (->before-interceptor
    (fn [{{[_ _ response] :event} :coeffects :as ctx}]
      (assoc-in ctx [:effects :db :response] response))))

(def remove-remote-response-interceptor
  (->before-interceptor
    (fn [ctx]
      (assoc-in ctx [:effects :db :response] nil))))

(defn- validate-todo-interceptor
  [& {:keys [:success :failure]}]
  (->before-interceptor
    (fn [{{[_ todo] :event} :coeffects :as ctx}]
      (-> ctx
          (update :queue concat (if (valid-todo? todo) success failure))
          (update :queue vec)))))

(defn- remote-store-interceptor
  [& {:keys [:success :failure :finally]}]
  (->before-interceptor
    (fn [{{event :event} :coeffects :as ctx}]
      (assoc-in ctx [:effects ::db/remote-store]
                {:method     :post
                 :url        "http://example.com/todo"
                 :todo       (second event)
                 :on-success [::db/chain event (concat success finally)]
                 :on-failure [::db/chain event (concat failure finally)]}))))

(re-frame/reg-event-ctx
  ::db/create-todo
  [begin-chain-interceptor
   remove-remote-failure-interceptor
   remove-remote-success-interceptor
   remove-validation-failure-interceptor
   remove-remote-response-interceptor
   (validate-todo-interceptor
     :success
     [(remote-store-interceptor
        :success
        [db-store-interceptor
         add-remote-success-interceptor]
        :failure
        [add-remote-failure-interceptor]
        :finally
        [db-store-response-interceptor])]
     :failure
     [add-validation-failure-interceptor])]
  identity)
