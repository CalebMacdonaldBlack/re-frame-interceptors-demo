(ns demo.events
  (:require [re-frame.core :as re-frame]
            [demo.db :as db]))

;=============== Helper ===============

(defn db-fx
  [{{:keys [:db]} :coeffects :as ctx}]
  (assoc-in ctx [:effects :db] db))

(re-frame/reg-event-db
  ::db/initialize-db
  (fn [_ _]
    db/default-db))

(defn ->vec [x]
  (flatten [x]))

(defn chain-if
  ([pred] (chain-if pred nil nil))
  ([pred success] (chain-if pred success nil))
  ([pred success failure]
   (fn [ctx]
     ((apply comp (reverse (->vec (if (pred ctx) success failure)))) ctx))))

(defn chain-fx
  ([fx-handler] (chain-fx fx-handler nil nil nil))
  ([fx-handler success] (chain-fx fx-handler success nil nil))
  ([fx-handler success failure] (chain-fx fx-handler success failure nil))
  ([fx-handler success failure finally]
   (fn [{{event :event} :coeffects :as ctx}]
     (fx-handler
       ctx
       [::db/chain event (concat (->vec success) (->vec finally))]
       [::db/chain event (concat (->vec failure) (->vec finally))]))))

;=============== Chain ===============

(re-frame/reg-event-ctx
  ::db/chain
  (fn [{{[_ event functions response] :event} :coeffects :as ctx}]
    ((apply comp (reverse (cons db-fx functions))) (assoc-in ctx [:coeffects :event] (conj event response)))))

(defn reg-event-chain
  ([id functions] (reg-event-chain id [] functions))
  ([id interceptors functions] (re-frame/reg-event-ctx id interceptors (apply comp (reverse (cons db-fx (->vec functions)))))))

;=============== FX ===============

(re-frame/reg-fx
  ::db/remote-store
  (fn [{:keys [:method :url :todo :on-success :on-failure]}]
    (if (re-find #"success" todo)
      (re-frame/dispatch (conj on-success "id-123"))
      (re-frame/dispatch (conj on-failure "Connection error")))))

;=============== Functions ===============

(defn remove-remote-success [ctx]
  (assoc-in ctx [:effects :db :remote-success] false))

(defn remove-remote-failure [ctx]
  (assoc-in ctx [:effects :db :remote-failure] false))

(defn remove-validation-failure [ctx]
  (assoc-in ctx [:effects :db :validation-failure] false))

(defn add-remote-success [ctx]
  (assoc-in ctx [:effects :db :remote-success] true))

(defn add-remote-failure [ctx]
  (assoc-in ctx [:effects :db :remote-failure] true))

(defn add-validation-failure [ctx]
  (assoc-in ctx [:effects :db :validation-failure] true))

(defn db-store
  [{{[_ todo] :event} :coeffects :as ctx}]
  (assoc-in ctx [:effects :db :todo] todo))

(defn db-store-response
  [{{[_ _ response] :event} :coeffects :as ctx}]
  (assoc-in ctx [:effects :db :response] response))

(defn remove-remote-response [ctx]
  (assoc-in ctx [:effects :db :response] nil))

(defn valid-todo?
  [{{[_ todo] :event} :coeffects}]
  (> (count todo) 10))

(defn add-remote-store-fx
  [{{event :event} :coeffects :as ctx} on-success on-failure]
  (assoc-in ctx [:effects ::db/remote-store]
            {:method     :post
             :url        "http://example.com/todo"
             :todo       (second event)
             :on-success on-success
             :on-failure on-failure}))

(def clear-alerts
  [remove-remote-failure
   remove-remote-success
   remove-validation-failure
   remove-remote-response])

(reg-event-chain
  ::db/create-todo
  [clear-alerts
   (chain-if
     valid-todo?
     (chain-fx
       add-remote-store-fx
       [db-store
        add-remote-success]
       add-remote-failure
       db-store-response)
     add-validation-failure)])
