(ns eql.entrypoint
  (:require
   [clojure.core.async :as async :refer [go <!]]
   [com.wsscode.promesa.bridges.core-async]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
   [promesa.core :as p]
   [cognitect.transit :as t]
   [common.misc :refer [log log-debug log-error error-msg get-envvar]]
   [applied-science.js-interop :as j]))

(pco/defresolver test-slow-resolver []
  {::pco/output [::test-slow-response]}
  (go
    (<! (async/timeout 400))
    {::test-slow-response "done"}))

(def test-constant-resolver (pbir/constantly-resolver ::test-constant "I'm always the same"))

(def test-attr-resolver (pbir/single-attr-resolver ::test-attr-input ::test-attr-output #(str % " plus more")))

(def env (pci/register [test-slow-resolver
                        test-constant-resolver
                        test-attr-resolver]))

(defn handler [event context callback]
  (try
    (log-debug "In the try")
    (let [{:keys [body]} (-> event (js->clj :keywordize-keys true))
          r              (t/reader :json)
          w              (t/writer :json)
          eql-request    (->> body (t/read r))]
      (log-debug "In the let body")
      (p/let [res (p.a.eql/process env eql-request)]
        (log-debug "In promesa let body")
        (callback nil (clj->js {:statusCode 200 :body (t/write w res)}))))
    (catch js/Error err
      (log-error "caught error")
      (log-error (ex-cause err))
      (callback nil (clj->js {:statusCode 500 :body error-msg})))))

(comment

  (let [w           (t/writer :json)
        r           (t/reader :json)
        eql-request [::test-slow-response
                     ::test-constant
                     {'(:>/params-test {::test-attr-input "hello"})
                      [::test-attr-output]}]]
    (handler (j/lit {:body (t/write w eql-request)}) ;; event
             nil ;; context
             #(-> %2 (j/get :body) (->> (t/read r)) tap>)) ;; callback
    )
  )
