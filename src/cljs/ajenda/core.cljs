(ns ajenda.core
  (:require
    [clojure.string :as string]
    [clojure.walk :refer [postwalk]]
    [reagent.core :as r]))

(defn $ [this]
  (-> this r/dom-node js/$))

(defn ->camel-case [s]
  (string/replace s #"-{1,}\b." #(when-let [c (last %)] (.toUpperCase c))))

(defn events-handler [f]
  (fn [start end timezone callback]
    (f start end timezone #(callback (clj->js %)))))

(defn wrap-event-click
  "removes the event from calendar when the click handler returns nil"
  [f calendar]
  (fn [event js-event view]
    (let [id            (.-_id event)
          updated-event (f (js->clj event) view)]
      (if updated-event
        (do
          (doseq [[k v] (select-keys updated-event [:start :end :tip :title])]
            (goog.object/set event (name k) (clj->js v)))
          (.fullCalendar calendar "updateEvent" event))
        (.fullCalendar calendar "removeEvents" id)))))

(defn wrap-rerender-events
  "calls the select function and paints the event with the result"
  [f calendar]
  (fn [start end event view]
    (when-let [event (f start end (js->clj event) view)]
      (.fullCalendar calendar "renderEvent" (clj->js event)))))

(defn rename-keys [opts]
  (postwalk
    (fn [node]
      (if (keyword? node)
        (keyword (->camel-case (name node)))
        node))
    opts))

(defn parse-opts [calendar opts]
  (-> (rename-keys opts)
      (update :eventClick wrap-event-click calendar)
      (update :select wrap-rerender-events calendar)
      (update :events events-handler)
      (clj->js)))

(defn calendar [opts]
  (let []
    (r/create-class
      {:display-name           "calendar"
       :component-did-mount    (fn [this]
                                 (let [calendar ($ this)]
                                   (.fullCalendar calendar (parse-opts calendar opts))))
       :component-did-update   (fn [this _]
                                 (-> this $ (.fullCalendar "render")))
       :component-will-unmount (fn [this]
                                 (-> this $ (.fullCalendar "destroy")))
       :reagent-render         (fn [_] [:div.calendar])})))
