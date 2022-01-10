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
   [promesa.core :as promesa]

   [common.misc :refer [log log-debug log-error error-msg get-envvar]]
   [eql.stripe.resolvers]
   [eql.registry :refer [resolvers]]
   ))

(pco/defresolver test-slow-resolver []
  {::pco/output [:test/slow-response]}
  (go
    (<! (async/timeout 400))
    {:test/slow-response "done"}))

(defn handler [event context callback]
  (try
    (let [{:keys [body]} (-> event (js->clj :keywordize-keys true))
          sub            (-> event (j/get-in [:requestContext :authorizer :claims :sub]))
          email          (-> event (j/get-in [:requestContext :authorizer :claims :email]))
          r              (t/reader :json)
          w              (t/writer :json)
          eql-req        (->> body (t/read r))
          validity       (atom {:valid true})
          ;; It should be ok to make the index in a let since every invocation is a new runtime
          index          (pci/register
                           (-> @resolvers
                               ;; TODO 2022-01-09 Justin How can I set these in the "context" of resolution without tampering with the eql-req?
                               ;; When that is refactored this can go back into a def and there will be less of a chance of
                               ;; the "tried to register duplicated resolver" error at the repl
                               (conj (pbir/constantly-resolver :eql.cognito/sub sub))
                               (conj (pbir/constantly-resolver :eql.cognito/email email))))]
      (->> eql-req
           (postwalk #(when (and (keyword? %)
                                 (namespace %)
                                 (includes? (namespace %) "eql"))
                        (reset! validity {:valid               false
                                          :invalid-request-key %}))))
      (if (-> @validity :valid)
        (promesa/let [res (p.a.eql/process index eql-req)]
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
    (handler (j/lit {:body           (t/write w req)
                     :requestContext {:authorizer {:claims {:sub   "45c371ee-a4a5-4a2f-aa82-b3434a7371ad"
                                                            :email "jgoodhcg+bbtest1@gmail.com"}}}}) ;; event
             nil ;; context
             #(-> %2 (j/get :body) (->> (t/read r)) tap>)) ;; callback
    )

  ;; This side effects and shouldn't be run more than once in the same runtime
  (def index (pci/register
               (-> @resolvers
                   (conj (pbir/constantly-resolver :eql.cognito/sub "45c371ee-a4a5-4a2f-aa82-b3434a7371ad"))
                   (conj (pbir/constantly-resolver :eql.cognito/email "jgoodhcg+bbtest1@gmail.com")))))

  ;; This is useful for testing "private" resolvers "eql.*"
  (promesa/let [req   [:eql.stripe.resolvers/stripe-id :eql.cognito/email]
                res   (->> req (p.a.eql/process index))]
    (tap> res))
  )
