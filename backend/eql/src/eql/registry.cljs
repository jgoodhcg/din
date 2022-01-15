(ns eql.registry)

(def items (atom []))

(defn add-resolvers-or-mutations!
  [new-items]
  (swap! items #(-> % (concat new-items) vec)))
