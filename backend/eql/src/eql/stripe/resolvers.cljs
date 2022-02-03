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
   [eql.registry :refer [add-resolvers-or-mutations!]]
   ))

(defn stripe-customer-xform
  [stripe-customer]
  (let [sub (-> stripe-customer :metadata :sub)
        free-pass (-> stripe-customer :metadata :free_pass)]
    (-> stripe-customer
        (merge {:eql.cognito/sub sub
                :stripe/free-pass free-pass})
        (rename-keys {:email :user/email :id ::customer-id})
        (select-keys [:user/email :eql.cognito/sub ::customer-id :stripe/free-pass]))))

(defn stripe-obj-xform
  [stripe-obj kmap]
  (-> stripe-obj
      (rename-keys kmap)
      (select-keys (vals kmap))))

(def stripe-key (pbir/constantly-resolver ::stripe-key (get-envvar :STRIPE_KEY)))

(def stripe-p-key (pbir/constantly-resolver :stripe/publishable-key (get-envvar :STRIPE_P_KEY)))

;; (def stripe-client (pbir/single-attr-resolver ::stripe-key ::stripe-client #(stripe-construct %)))
(def stripe-client (pbir/constantly-resolver ::stripe-client (stripe-construct (get-envvar :STRIPE_KEY))))

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

(pco/defresolver stripe-customer!
  [{email                    :user/email
    sub                      :eql.cognito/sub
    <get-customers-for-email ::<get-customers-for-email-fn
    <create-customer         ::<create-customer-fn}]
  {::pco/output [::customer-id :stripe/free-pass]}
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

(comment
  (let [stripe (stripe-construct (get-envvar :STRIPE_KEY))
        params (-> {;; :eql.cognito/sub "45c371ee-a4a5-4a2f-aa82-b3434a7371ad"
                    ;; :user/email      "jgoodhcg+bbtest1@gmail.com"
                    :eql.cognito/sub "3ac0d472-4d29-4064-9e42-cc1376997220"
                    :user/email      "jgoodhcg+bbtest2@gmail.com"}
                   (merge (<get-customers-for-email-fn {::stripe-client stripe}))
                   (merge (<create-customer-fn {::stripe-client stripe})))]
    (go
      (-> params
        stripe-customer
        <!
        tap>)))
  )

(pco/defresolver products
  [{stripe ::stripe-client
    customer-id ::customer-id}]
  {::pco/output [{::products
                  [::product-id
                   :stripe.product/description
                   :stripe.product/name
                   :stripe.product/images]}]}
  (go
     (-> stripe
         (j/get :products)
         (j/call :list (j/lit {:limit 100}))
         <p!
         (js->clj :keywordize-keys true)
         ((fn [{:keys [has_more data]}]
            (when has_more (throw "We have more than 100 products, time to write a loop!"))
            {::products (->> data
                             (mapv #(stripe-obj-xform % {:id          ::product-id
                                                         :name        :stripe.product/name
                                                         :description :stripe.product/description
                                                         :images      :stripe.product/images})))})))))

(pco/defresolver prices
  [{stripe ::stripe-client
    customer-id ::customer-id}]
  {::pco/output [{:stripe/prices [:stripe.price/id
                                  :stripe.price/unit-amount
                                  ::product-id]}]}
  (go
    (-> stripe
        (j/get :prices)
        (j/call :list (j/lit {:limit 100}))
        <p!
        (js->clj :keywordize-keys true)
        ((fn [{:keys [has_more data]}]
           (when has_more (throw "We have more than 100 prices, time to write a loop!"))
           {:stripe/prices (->> data
                                (mapv #(stripe-obj-xform % {:id          :stripe.price/id
                                                            :unit_amount :stripe.price/unit-amount
                                                            :product     ::product-id})))})))))

(comment
  (let [stripe-client (stripe-construct (get-envvar :STRIPE_KEY))]
    (go
      (-> {::stripe-client stripe-client}
          prices ;; swap out products
          ;; products
          <!
          tap>)))
  )

(pco/defresolver product
  [{stripe     ::stripe-client
    product-id ::product-id}]
  {::pco/output [::product-id
                 :stripe.product/description
                 :stripe.product/name
                 :stripe.product/images]}
  (go
    (-> stripe
        (j/get :products)
        (j/call :retrieve product-id)
        <p!
        (js->clj :keywordize-keys true)
        (stripe-obj-xform {:id          ::product-id
                           :name        :stripe.product/name
                           :description :stripe.product/description
                           :images      :stripe.product/images}))))

(pco/defresolver price
  [{stripe   ::stripe-client
    price-id :stripe.price/id}]
  {::pco/output [:stripe.price/id
                 :stripe.price/unit-amount
                 ::product-id]}
  (go
    (-> stripe
        (j/get :prices)
        (j/call :retrieve price-id)
        <p!
        (js->clj :keywordize-keys true)
        (stripe-obj-xform {:id :stripe.price/id :product ::product-id :unit_amount :stripe.price/unit-amount}))))

(comment
  (let [stripe-client (stripe-construct (get-envvar :STRIPE_KEY))
        ;; product-id    "prod_KwFuyzREssNGFu"
        price-id      "price_1KGNOkBAaAf4dYG6cMdtieqH"]
    (go
      (-> {::stripe-client  stripe-client
           ;; ::product-id product-id
           :stripe.price/id price-id}
          price ;; swap out product
          <!
          tap>)))
  )

(pco/defresolver subscriptions
  [{stripe      ::stripe-client
    customer-id ::customer-id}]
  {::pco/output [{::subscriptions [::subscription-id]}]}
  (go
     (-> stripe
         (j/get :subscriptions)
         (j/call :list (j/lit {:limit    100
                               :customer customer-id}))
         <p!
         (js->clj :keywordize-keys true)
         ((fn [{:keys [has_more data]}]
            (when has_more (throw "A Customer has been found with more than 100 subscriptions, time to write a loop!"))
            {::subscriptions (->> data
                                  (mapv #(stripe-obj-xform % {:id ::subscription-id})))})))))

(comment
  (let [stripe-client (stripe-construct (get-envvar :STRIPE_KEY))
        customer-id     "cus_KvZTT4PMdlahLM"]
    (go
      (-> {::stripe-client stripe-client
           ::customer-id     customer-id}
          subscriptions
          <!
          tap>)))
  )

(pco/defresolver active-subscription
  [{stripe      ::stripe-client
    customer-id ::customer-id}]
  {::pco/output [:stripe/has-active-subscription?]}
  (go
    {:stripe/has-active-subscription?
     (-> stripe
         (j/get :subscriptions) ;; TODO utilize subscriptions resolver?
         (j/call :list (j/lit {:limit    100
                               :customer customer-id
                               :status   "active"}))
         <p!
         (js->clj :keywordize-keys true)
         ((fn [{:keys [has_more data]}]
            (when has_more (throw "A Customer has been found with more than 100 subscriptions, time to write a loop!"))
            data))
         (#(-> % count (> 0))))}))

(comment
  (let [stripe-client (stripe-construct (get-envvar :STRIPE_KEY))
        ;; customer-id     "cus_KvZTT4PMdlahLM"
        customer-id   "cus_KzHJ9KPtx7xEqJ"]
    (go
      (-> {::stripe-client stripe-client
           ::customer-id   customer-id}
          active-subscription
          <!
          tap>)))
  )

(pco/defresolver <get-setup-intents-fn
  [{stripe ::stripe-client}]
  {::<get-setup-intents-fn
   (fn [{:keys [customer-id limit starting-after]}]
     (go
       (-> stripe
           (j/get :setupIntents)
           (j/call :list (cond-> {:customer customer-id}
                           (some? limit)
                           (merge {:limit limit})
                           (some? starting-after)
                           (merge {:starting_after starting-after})
                           true clj->js))
           <p!
           (js->clj :keywordize-keys true))))})

(pco/defresolver setup-intents
  [{<get-setup-intents-fn ::<get-setup-intents-fn
    customer-id           ::customer-id}]
  {::pco/output [{::setup-intents [::setup-intent-id ::setup-intent-status ::customer-id]}]}
  (go
    (let [first-si-batch (<! (<get-setup-intents-fn {:customer-id customer-id :limit 100}))]
      (<! (go-loop [this-batch first-si-batch
                    setup-intents (-> first-si-batch :data vec)]
            (if (or (-> this-batch :has_more not)
                    (empty? setup-intents))
              {::setup-intents (->> setup-intents
                                    (mapv #(stripe-obj-xform % {:id            ::setup-intent-id
                                                                :status        ::setup-intent-status
                                                                :client_secret :stripe/setup-intent-client-secret
                                                                :customer      ::customer-id})))}
              (recur (<! (<get-setup-intents-fn
                          {:customer-id    customer-id
                           :limit          100
                           :starting_after (-> this-batch last :id)}))
                         setup-intents)))))))

(comment
  (let [stripe      (stripe-construct (get-envvar :STRIPE_KEY))
        ;; customer-id "cus_KvZTT4PMdlahLM"
        customer-id "cus_KzHJ9KPtx7xEqJ"]
    (go
      (-> {::customer-id customer-id}
          (merge (<get-setup-intents-fn {::stripe-client stripe}))
          setup-intents
          <!
          tap>))
    )
  )

(add-resolvers-or-mutations! [stripe-key
                              stripe-p-key
                              stripe-client
                              <get-customers-for-email-fn
                              <create-customer-fn
                              stripe-customer!
                              products
                              prices
                              product
                              price
                              subscriptions])
