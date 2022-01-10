(ns eql.stripe.resolvers
  (:require
   ["stripe" :as stripe-construct]

   [applied-science.js-interop :as j]
   [cljs.core.async :refer [go <! go-loop]]
   [cljs.core.async.interop :refer [<p!]]
   [clojure.set :refer [rename-keys]]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
   [potpuri.core :as p]

   [common.misc :refer [log-debug log-error error-msg get-envvar]]
   [eql.registry :refer [add-resolvers]]
   ))

(defn stripe-customer-xform
  [stripe-customer]
  (let [sub (-> stripe-customer :metadata :sub)]
    (-> stripe-customer
        (merge {:eql.cognito/sub sub})
        (rename-keys {:email :user/email :id ::stripe-id})
        (select-keys [:user/email :eql.cognito/sub ::stripe-id]))))

(def stripe-key (pbir/constantly-resolver ::stripe-key (get-envvar :STRIPE_KEY)))

(def stripe-client (pbir/single-attr-resolver ::stripe-key ::stripe-client #(stripe-construct %)))

(pco/defresolver <get-customers-for-email-fn
  [{stripe ::stripe-client}]
  {::<get-customers-for-email-fn
   (fn [{:keys [email limit starting-after]}]
     (go
       (-> stripe
           (j/get :customers)
           (j/call :list (cond-> {:email email}
                           (some? limit)
                           (merge {:limit limit})
                           (some? starting-after)
                           (merge {:starting_after starting-after})
                           true clj->js))
           <p!
           (js->clj :keywordize-keys true))))})

(pco/defresolver <create-customer-fn
  [{stripe ::stripe-client}]
  {::<create-customer-fn
   (fn [{:keys [email sub]}]
     (go
       (-> stripe
           (j/get :customers)
           (j/call :create (j/lit {:email email :metadata {:sub sub}}))
           <p!
           (js->clj :keywordize-keys true))))})

(pco/defresolver stripe-id
  [{email                    :eql.cognito/email
    <get-customers-for-email ::<get-customers-for-email-fn
    <create-customer         ::<create-customer-fn
    sub                      :eql.cognito/sub}]
  {::pco/output [::stripe-id]}
  (go
    (let [first-customer-batch (<! (<get-customers-for-email {:email email :limit 100}))]
      (<! (go-loop [customer-batch first-customer-batch]
            (let [matching-customer (->> customer-batch
                                         :data
                                         (some #(when (= sub (get-in % [:metadata :sub])) %)))]
              (if (some? matching-customer)
                (-> matching-customer stripe-customer-xform)
                (if (or (-> customer-batch :has_more not) (empty? customer-batch))
                  (-> (<! (<create-customer (p/map-of email sub)))
                      stripe-customer-xform)
                  (recur (<! (<get-customers-for-email
                               {:email          email
                                :limit          100
                                :starting_after (-> customer-batch last :id)})))))))))))

(add-resolvers [stripe-key
                stripe-client
                <get-customers-for-email-fn
                <create-customer-fn
                stripe-id])

(comment
  (go
    (let [stripe (-> :STRIPE_KEY get-envvar stripe-construct)
          email  "jgoodhcg+bbtest1@gmail.com"]
      (-> (p/map-of stripe email) <get-customers <! tap>))))

(comment
  (defn <get-customers [{:keys [stripe email limit starting-after]}]
    (go
      (-> stripe
          (j/get :customers)
          (j/call :list (cond-> {:email email}
                          (some? limit)
                          (merge {:limit limit})
                          (some? starting-after)
                          (merge {:starting_after starting-after})
                          true clj->js))
          <p!
          (js->clj :keywordize-keys true))))

  (defn <create-customer [{:keys [stripe email sub]}]
    (go
      (-> stripe
          (j/get :customers)
          (j/call :create (j/lit {:email email :metadata {:sub sub}}))
          <p!
          (js->clj :keywordize-keys true))))

  (defn handler [event context callback]
    (try
      (log-debug "In the try")
      (let [{:keys [body]}      (-> event (js->clj :keywordize-keys true))
            {:keys [sub email]} (-> body js/JSON.parse (js->clj :keywordize-keys true))
            stripe              (stripe-construct (get-envvar :STRIPE_KEY))
            limit               100]
        (log-debug "In the let body")
        (go
          (log-debug "In outer go")
          (let [first-customer-batch (<! (<get-customers (p/map-of stripe email limit)))]
            (log-debug (str "first customer batch size: " (count first-customer-batch)))
            (go-loop [customer-batch first-customer-batch]
              (log-debug "In go-loop")
              (log-debug (str "Current customer batch size " (count customer-batch)))
              (let [matching-customer (->> customer-batch
                                           :data
                                           (some #(when (= sub (get-in % [:metadata :sub])) %)))]
                (if (some? matching-customer)
                  (do (log-debug (str "Matching customer found " matching-customer))
                      matching-customer)
                  (if (or (-> customer-batch :has_more not) (empty? customer-batch))
                    (do (log-debug (str "Exhausted customer list, creating customer " (p/map-of email sub)))
                        (<! (<create-customer (p/map-of stripe email sub))))
                    (do (log-debug "Getting next customer batch")
                        (recur (<! (<get-customers (merge (p/map-of stripe email)
                                                          {:starting_after (-> customer-batch last :id)}))))))))))))
      (catch js/Error err
        (log-error "caught error")
        (log-error (ex-cause err))
        (callback nil (clj->js {:statusCode 500 :body error-msg})))))

  )
