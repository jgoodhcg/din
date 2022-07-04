(ns pg
  (:require [clojure.string :refer [split join replace]]))

;; hyphens in the ns part will not work, only periods
;; this.is.bad-will-not/work
;; this.is.good/will-work
;; TODO 2021-07-04 Justin - I'm not sure if I should address this or how I might without turning `/` into `___`
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
      (replace #":" "")
      (replace #"-" "_")))

(comment
  (-> :roam.sync/latest k->pg pg->k)
  (-> :a.b/c-e k->pg pg->k)
  )
