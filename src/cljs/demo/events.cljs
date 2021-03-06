(ns demo.events
  (:require [re-frame.core :as re-frame]
            [demo.db :as db]))

;=============== Helper ===============

(defn ->before-interceptor [fn]
  {:id     :before-interceptor
   :before fn
   :after  identity})

(re-frame/reg-event-db
  ::db/initialize-db
  (fn [_ _]
    db/default-db))

(defn ->vec [x]
  (flatten [x]))

(defn if-interceptor
  ([pred] (if-interceptor pred nil nil))
  ([pred success] (if-interceptor pred success nil))
  ([pred success failure]
   (->before-interceptor
     (fn [ctx]
       (-> ctx
           (update :queue (partial concat (if (pred ctx) (->vec success) (->vec failure))))
           (update :queue vec))))))

(defn try-interceptor
  ([ctx-modifier] (try-interceptor ctx-modifier nil nil nil))
  ([ctx-modifier success] (try-interceptor ctx-modifier success nil nil))
  ([ctx-modifier success failure] (try-interceptor ctx-modifier success failure nil))
  ([ctx-modifier success failure finally]
   (->before-interceptor
     (fn [{{event :event} :coeffects :as ctx}]
       (ctx-modifier
         ctx
         [::db/chain event (concat (->vec success) (->vec finally))]
         [::db/chain event (concat (->vec failure) (->vec finally))])))))

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

(defn reg-event-chain [id & interceptors]
  (re-frame/reg-event-ctx id (vec (cons begin-chain-interceptor interceptors)) identity))

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
    #(assoc-in % [:effects :db :remote-success] false))) ;;BL

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

(reg-event-chain
  ::db/create-todo
  remove-remote-failure-interceptor
  remove-remote-success-interceptor
  remove-validation-failure-interceptor
  remove-remote-response-interceptor
  (if-interceptor
    valid-todo?
    (try-interceptor
      add-remote-store-fx
      [db-store-interceptor add-remote-success-interceptor]
      add-remote-failure-interceptor
      db-store-response-interceptor)
    add-validation-failure-interceptor))
