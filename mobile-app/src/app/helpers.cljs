(ns app.helpers
  (:require
   [clojure.set :refer [map-invert]]
   [re-frame.core :refer [subscribe dispatch dispatch-sync]]
   ["tailwind-rn" :default tailwind-rn]
   ))

(def <sub (comp deref subscribe))

(def >evt dispatch)

(def >evt-sync dispatch-sync)

(defn tw [style-str]
  ;; https://github.com/vadimdemedes/tailwind-rn#supported-utilities
  (-> style-str
      tailwind-rn
      (js->clj :keywordize-keys true)))

(def screen-key-name-mapping #:screen {:feed-item    "Feed Item"
                                       :feed         "Feed"
                                       :feeds        "Feeds"
                                       :payment      "Payment"
                                       :subscription "Subscription"
                                       :settings     "Settings"
                                       :account      "Account"
                                       :login        "Login"
                                       :signup       "Signup"})

(defn screen-key->name [k] (get screen-key-name-mapping k))

(defn screen-name->key [n] (-> screen-key-name-mapping map-invert (get n)))

(defn pad [n] (if (-> n str count (< 2))
                (str "0" n)
                (str n)))

(defn millis->str [millis]
  ;; TODO add padding
  ;; https://stackoverflow.com/a/9763769/5040125
  (let [ms   (rem millis 1000)
        left (-> millis (- ms) (/ 1000))
        sec  (-> left (rem 60))
        left (-> left (- sec) (/ 60))
        min  (-> left (rem 60))
        hr   (-> left (- min) (/ 60))]
    (str (pad hr) ":" (pad min) ":" (pad sec))))

(defn percent-of-duration [position duration]
  (if (and (some? position)
           (some? duration))
    (-> position
        (/ duration)
        (* 100)
        (str "%"))
    "0%"))
