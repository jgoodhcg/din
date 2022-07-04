(ns pg
  (:require [clojure.string :refer [split join replace]]))

(defn pg->k [table-or-column-name]
  (let [[n k] (-> table-or-column-name (split "__"))
      ns-part (-> n (split "_") (->> (join ".")))
      key-part (-> k (split "_") (->> (join "-")))]

  (keyword (str ns-part "/" key-part))))

(defn k->pg [k]
  (-> k
      str
      (replace #"/" "__")
      (replace #"\." "_")
      (replace #":" "")))

(comment
  (-> :roam.sync/latest k->pg pg->k)
  (-> :a.b/c-e k->pg pg->k)
  )
