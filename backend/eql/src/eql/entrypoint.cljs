(ns eql.entrypoint
  (:require
   [clojure.core.async :as async :refer [go <!]]
   [com.wsscode.promesa.bridges.core-async]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
   [promesa.core :as p]))

(pco/defresolver slow-resolver []
  {::pco/output [::slow-response]}
                                        ; returning a channel from resolver
  (go
    (<! (async/timeout 400))
    {::slow-response "done"}))

(def env (pci/register slow-resolver))

(defn handler [] "hello")

(comment
  (p/let [res (p.a.eql/process env [::slow-response])]
    (tap> res)))
