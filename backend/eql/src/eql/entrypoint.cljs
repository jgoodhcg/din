(ns eql.entrypoint
  (:require
   [applied-science.js-interop :as j]
   [clojure.core.async :as async :refer [go <!]]
   [clojure.walk :refer [postwalk]]
   [clojure.string :refer [includes?]]
   [cognitect.transit :as t]
   [com.wsscode.promesa.bridges.core-async]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
   [promesa.core :as p]

   [common.misc :refer [log log-debug log-error error-msg get-envvar]]
   [eql.stripe.resolvers :refer [stripe-key stripe-client customers]]
   ))

(pco/defresolver test-slow-resolver []
  {::pco/output [:test/slow-response]}
  (go
    (<! (async/timeout 400))
    {:test/slow-response "done"}))

(def test-constant-resolver (pbir/constantly-resolver :test/constant "I'm always the same"))

(def test-attr-resolver (pbir/single-attr-resolver :test/attr-input :test/attr-output #(str % " plus more")))

(def env (pci/register [test-slow-resolver
                        test-constant-resolver
                        test-attr-resolver
                        stripe-key
                        stripe-client
                        customers
                        ]))

(defn handler [event context callback]
  (try
    (log-debug "In the try")
    (let [{:keys [body]} (-> event (js->clj :keywordize-keys true))
          r              (t/reader :json)
          w              (t/writer :json)
          eql-req        (->> body (t/read r))
          validity       (atom {:valid true})]
      (log-debug "Validating query")
      (->> eql-req
           (postwalk #(when (and (keyword? %)
                                 (namespace %)
                                 (includes? (namespace %) "eql"))
                        (reset! validity {:valid               false
                                          :invalid-request-key %}))))
      (log-debug "In the let body")
      (if (-> @validity :valid)
        (p/let [res (p.a.eql/process env eql-req)]
          (log-debug "In promesa let body")
          (callback nil (j/lit {:statusCode 200 :body (t/write w res)})))
        (do
          (log-debug @validity)
          (callback nil (j/lit {:statusCode 500 :body (t/write w @validity)})))))
    (catch js/Error err
      (log-error "caught error")
      (log-error (ex-cause err))
      (callback nil (clj->js {:statusCode 500 :body error-msg})))))

(comment

  (let [w   (t/writer :json)
        r   (t/reader :json)
        req [:test/slow-response
             :test/constant
             {'(:>/params-test {:test/attr-input "hello"})
              [:test/attr-output]}]]
    (handler (j/lit {:body (t/write w req)}) ;; event
             nil ;; context
             #(-> %2 (j/get :body) (->> (t/read r)) tap>)) ;; callback
    )

  (p/let [req [:eql.stripe.resolvers/customers]
          res (p.a.eql/process env req)]
    (tap> res))
  )
