(ns eql.entrypoint
  (:require
   [applied-science.js-interop :as j]
   [clojure.walk :refer [postwalk]]
   [clojure.string :refer [includes?]]
   [cognitect.transit :as t]
   [com.wsscode.promesa.bridges.core-async]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.built-in.plugins :as pbip]
   [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
   [com.wsscode.pathom3.plugin :as p.plugin]
   [promesa.core :as promesa]

   [common.misc :refer [log-debug log-error error-msg]]
   [eql.registry :refer [items]]

   [eql.stripe.resolvers]
   [eql.stripe.mutations]

   ))

(def index (-> (pci/register @items)
               (p.plugin/register pbip/mutation-resolve-params)))

(defn handler [event context callback]
  (try
    (let [{:keys [body]} (-> event (js->clj :keywordize-keys true))
          sub            (-> event (j/get-in [:requestContext :authorizer :claims :sub]))
          email          (-> event (j/get-in [:requestContext :authorizer :claims :email]))
          r              (t/reader :json)
          w              (t/writer :json)
          eql-req        (->> body (t/read r))
          validity       (atom {:valid true})]

      (->> eql-req
           (postwalk #(when (and (keyword? %)
                                 (namespace %)
                                 (includes? (namespace %) "eql"))
                        (reset! validity {:valid               false
                                          :invalid-request-key %}))))
      (if (-> @validity :valid)
        (promesa/let [response (->> `[{(:>/res {:eql.cognito/sub ~sub
                                                :eql.cognito/email ~email}) ~eql-req}]
                                    (p.a.eql/process index))
                      result (get response :>/res)]
          (callback nil (j/lit {:statusCode 200 :body (t/write w result)})))
        (do
          (log-debug @validity)
          (callback nil (j/lit {:statusCode 500 :body (t/write w @validity)})))))
    (catch js/Error err
      (log-error "caught error")
      (log-error (ex-cause err))
      (callback nil (clj->js {:statusCode 500 :body error-msg})))))

(comment

  ;; Test the full handler
  (let [w   (t/writer :json)
        r   (t/reader :json)
        req []]
    (handler (j/lit {:body           (t/write w req)
                     :requestContext {:authorizer {:claims {:sub   "45c371ee-a4a5-4a2f-aa82-b3434a7371ad"
                                                            :email "jgoodhcg+bbtest1@gmail.com"}}}}) ;; event
             nil ;; context
             #(-> %2 (j/get :body) (->> (t/read r)) tap>)) ;; callback
    )

  ;; This is useful for testing "private" resolvers "eql.*"
  (promesa/let [req   [{'(:>/req {:eql.cognito/sub "45c371ee-a4a5-4a2f-aa82-b3434a7371ad"
                                  :eql.cognito/email "jgoodhcg+bbtest1@gmail.com"})
                        [:eql.stripe.resolvers/stripe-id]}]
                res   (->> req (p.a.eql/process index))]
    (tap> res))
  )
