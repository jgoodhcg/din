(ns backend.main
  (:require [cljs.nodejs.shell :refer [sh]]
            [clojure.string :refer [split join]]
            [clojure.edn :as edn]))

(defn handler [_ _ cb]
  (cb nil
      #js {:statusCode 200
           :body       (js/JSON.stringify "Hello from Shadow")}))

(comment

  (def result (atom nil))

  (handler nil nil #(tap> %2))

  (reset! result (:out (sh "npx" "roam-api"
                           "-g" "xxx"
                           "-e" "xxx"
                           "-p" "xxx"
                           "query" "'[:find ?n :where [?e :node/title ?n]]'")))

  (-> @result
      .toString
      (split #"\n")
      (->> (drop 2))
      join
      edn/read-string)

  (-> [
       "["
       "[" "1" "]"
       "[" "2" "]"
       "]"
       ]
      join
      edn/read-string
      second)
  )
