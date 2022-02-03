(ns eql.stripe.mutations
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
   [eql.registry :refer [add-resolvers-or-mutations!]]
   [eql.stripe.resolvers :refer [stripe-obj-xform]]
   ))

;; 2022-02-03 Justin - Unused
(pco/defmutation create-subscription
  [{stripe      :eql.stripe.resolvers/stripe-client
    customer-id   :eql.stripe.resolvers/customer-id
    price-id    :stripe/price-id}]
  {::pco/output [:eql.stripe.resolvers/subscription-id]}
  (go
    (-> stripe
        (j/get :subscriptions)
        (j/call :create (j/lit {:customer customer-id :items [{:price price-id}]}))
        <p!
        (js->clj :keywordize-keys true)
        (stripe-obj-xform {:id :eql.stripe.resolvers/subscription-id}))))

(comment
  (let [stripe-client (stripe-construct (get-envvar :STRIPE_KEY))
        customer-id    "cus_KvZTT4PMdlahLM"
        price-id      "price_1KGNOkBAaAf4dYG6cMdtieqH"]
    (go
      (-> {:eql.stripe.resolvers/stripe-client stripe-client
           :eql.stripe.resolvers/customer-id customer-id
           :stripe/price-id price-id}
          create-subscription
          <!
          tap>)))
  )

;; 2022-02-03 Justin - Unused
(pco/defmutation create-setup-intent
  [{stripe      :eql.stripe.resolvers/stripe-client
    customer-id :eql.stripe.resolvers/customer-id}]
  {::pco/output [:eql.stripe.resolvers/setup-intent-id
                 :stripe/setup-intent-client-secret]}
  (go
    (-> stripe
        (j/get :setupIntents)
        (j/call :create (j/lit {:customer customer-id :payment_method_types ["card"]}))
        <p!
        (js->clj :keywordize-keys true)
        (stripe-obj-xform {:id            :eql.stripe.resolvers/setup-intent-id
                           :client_secret :stripe/setup-intent-client-secret}))))

(comment
  (let [stripe-client (stripe-construct (get-envvar :STRIPE_KEY))
        ;; customer-id    "cus_KvZTT4PMdlahLM"
        customer-id   "cus_KzHJ9KPtx7xEqJ"]
    (go
      (-> {:eql.stripe.resolvers/stripe-client stripe-client
           :eql.stripe.resolvers/customer-id   customer-id}
          create-setup-intent
          <!
          tap>)))
  )

(pco/defmutation create-checkout-session
  [{success-url :stripe.checkout/success-url
    cancel-url  :stripe.checkout/cancel-url
    price-id    :stripe/price-id
    stripe      :eql.stripe.resolvers/stripe-client
    customer-id :eql.stripe.resolvers/customer-id}]
  {::pco/output [:stripe.checkout/checkout-url]}
  (go
    (-> stripe
        (j/get :checkout)
        (j/get :sessions)
        (j/call :create (j/lit {:mode        "subscription"
                                :customer    customer-id
                                :line_items  [{:price    price-id
                                               :quantity 1}]
                                :success_url success-url
                                :cancel_url  cancel-url}))
        <p!
        (#(hash-map :stripe.checkout/checkout-url (j/get % :url))))))

(comment
  (let [stripe-client (stripe-construct (get-envvar :STRIPE_KEY))
        ;; customer-id    "cus_KvZTT4PMdlahLM"
        customer-id   "cus_KzHJ9KPtx7xEqJ"
        price-id      "price_1KGNOkBAaAf4dYG6cMdtieqH"]
    (go
      (-> {:stripe/price-id                    price-id
           ;; custom schemes don't work :(
           ;; :stripe.checkout/success-url        "exp://127.0.0.1:19000/success"
           ;; :stripe.checkout/cancel-url         "exp://127.0.0.1:19000/cancel"
           :stripe.checkout/success-url        "https://127.0.0.1:19000/success"
           :stripe.checkout/cancel-url         "https://127.0.0.1:19000/cancel"
           :eql.stripe.resolvers/stripe-client stripe-client
           :eql.stripe.resolvers/customer-id   customer-id}
          create-checkout-session
          <!
          tap>)))
  )

(add-resolvers-or-mutations! [create-subscription
                              create-setup-intent])
