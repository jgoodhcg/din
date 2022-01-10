(ns eql.registry)

(def resolvers (atom []))

(defn add-resolvers
  [new-resolvers]
  (swap! resolvers #(-> % (concat new-resolvers) vec)))
