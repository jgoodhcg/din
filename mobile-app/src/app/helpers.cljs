(ns app.helpers
  (:require [re-frame.core :refer [subscribe dispatch]]
            ["tailwind-rn" :default tailwind-rn]
            ))

(def <sub (comp deref subscribe))

(def >evt dispatch)

(defn tw [style-str]
  ;; https://github.com/vadimdemedes/tailwind-rn#supported-utilities
  (-> style-str
      tailwind-rn
      (js->clj :keywordize-keys true)))
