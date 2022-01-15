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
   ))

(pco/defmutation create-subscription
  [{stripe      :eql.stripe.resolvers/stripe-client
    stripe-id   :eql.stripe.resolvers/stripe-id
    price-id    :stripe/price-id}]
  {::pco/output [:eql.stripe.resolvers/subscription-id]}
  (go
    (-> stripe
        (j/get :subscriptions)
        (j/call :create (j/lit {:customer stripe-id :items [{:price price-id}]}))
        <p!
        (js->clj :keywordize-keys true)
        (rename-keys {:id :eql.stripe.resolvers/subscription-id}))))

(comment
  (let [stripe-client (stripe-construct (get-envvar :STRIPE_KEY))
        stripe-id    "cus_KvZTT4PMdlahLM"
        price-id      "price_1KGNOkBAaAf4dYG6cMdtieqH"]
    (go
      (-> {:eql.stripe.resolvers/stripe-client stripe-client
           :eql.stripe.resolvers/stripe-id stripe-id
           :stripe/price-id price-id}
          create-subscription
          <!
          tap>)))
  )

(pco/defmutation create-setup-intent
  [{stripe      :eql.stripe.resolvers/stripe-client
    stripe-id   :eql.stripe.resolvers/stripe-id}]
  {::pco/output [:eql.stripe.resolvers/setup-intent-id
                 :stripe/setup-intent-client-secret]}
  (go
    (-> stripe
        (j/get :setupIntents)
        (j/call :create (j/lit {:customer stripe-id :payment_method_types ["card"]}))
        <p!
        (js->clj :keywordize-keys true)
        (rename-keys {:id :eql.stripe.resolvers/setup-intent-id
                      :client_secret :stripe/setup-intent-client-secret}))))

(comment
  (let [stripe-client (stripe-construct (get-envvar :STRIPE_KEY))
        stripe-id    "cus_KvZTT4PMdlahLM"]
    (go
      (-> {:eql.stripe.resolvers/stripe-client stripe-client
           :eql.stripe.resolvers/stripe-id stripe-id}
          create-setup-intent
          <!
          tap>)))
  )

(add-resolvers-or-mutations! [create-subscription
                              create-setup-intent])
