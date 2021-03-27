(ns app.helpers
  (:require
   [clojure.set :refer [map-invert]]
   [re-frame.core :refer [subscribe dispatch]]
   ["tailwind-rn" :default tailwind-rn]
   ))

(def <sub (comp deref subscribe))

(def >evt dispatch)

(defn tw [style-str]
  ;; https://github.com/vadimdemedes/tailwind-rn#supported-utilities
  (-> style-str
      tailwind-rn
      (js->clj :keywordize-keys true)))

(def screen-key-name-mapping #:screen {:feed  "Feed"
                                       :feeds "Feeds"} )

(defn screen-key->name [k] (get screen-key-name-mapping k))
(defn screen-name->key [n] (-> screen-key-name-mapping map-invert (get n)))
