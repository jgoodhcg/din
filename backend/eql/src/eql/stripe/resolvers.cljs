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
        (rename-keys {:email :user/email :id ::stripe-id})
        (select-keys [:user/email :eql.cognito/sub ::stripe-id :stripe/free-pass]))))

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

(pco/defresolver stripe-customer
  [{email                    :user/email
    sub                      :eql.cognito/sub
    <get-customers-for-email ::<get-customers-for-email-fn
    <create-customer         ::<create-customer-fn}]
  {::pco/output [::stripe-id :user/email :stripe/free-pass]}
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
        params (-> {:eql.cognito/sub "45c371ee-a4a5-4a2f-aa82-b3434a7371ad"
                    :user/email      "jgoodhcg+bbtest1@gmail.com"}
                   (merge (<get-customers-for-email-fn {::stripe-client stripe}))
                   (merge (<create-customer-fn {::stripe-client stripe})))]
    (go
      (-> params
        stripe-customer
        <!
        tap>)))
  )

(pco/defresolver products
  [{stripe ::stripe-client}]
  {::pco/output [{::products [::product-id]}]}
  (go
     (-> stripe
         (j/get :products)
         (j/call :list (j/lit {:limit 100}))
         <p!
         (js->clj :keywordize-keys true)
         ((fn [{:keys [has_more data]}]
            (when has_more (throw "We have more than 100 products, time to write a loop!"))
            (->> data (mapv #(rename-keys % {:id ::product-id}))))))))

(pco/defresolver prices
  [{stripe     ::stripe-client}]
  {::pco/output [{::prices [::price-id ::product-id]}]}
  (go
     (-> stripe
         (j/get :prices)
         (j/call :list (j/lit {:limit 100}))
         <p!
         (js->clj :keywordize-keys true)
         ((fn [{:keys [has_more data]}]
            (when has_more (throw "We have more than 100 products, time to write a loop!"))
            (->> data (mapv #(rename-keys % {:id ::price-id :product ::product-id}))))))))

(comment
  (let [stripe-client (stripe-construct (get-envvar :STRIPE_KEY))]
    (go
      (-> {::stripe-client stripe-client}
          prices ;; swap out products
          <!
          tap>)))
  )

(pco/defresolver product
  [{stripe     ::stripe-client
    product-id ::product-id}]
  {::pco/output [::product-name ::product-id]}
  (go
    (-> stripe
        (j/get :products)
        (j/call :retrieve product-id)
        <p!
        (js->clj :keywordize-keys true)
        (rename-keys {:id ::product-id :name ::product-name}))))

(pco/defresolver price
  [{stripe     ::stripe-client
    price-id ::price-id}]
  {::pco/output [::price-id ::product-id ::unit-mount]}
  (go
    (-> stripe
        (j/get :prices)
        (j/call :retrieve price-id)
        <p!
        (js->clj :keywordize-keys true)
        (rename-keys {:id ::price-id :product ::product-id :unit_amount ::unit-amount}))))

(comment
  (let [stripe-client (stripe-construct (get-envvar :STRIPE_KEY))
        ;; product-id    "prod_KwFuyzREssNGFu"
        price-id      "price_1KGNOkBAaAf4dYG6cMdtieqH"]
    (go
      (-> {::stripe-client stripe-client
           ;; ::product-id product-id
           ::price-id price-id}
          price ;; swap out product
          <!
          tap>)))
  )

(add-resolvers-or-mutations! [stripe-key
                              stripe-client
                              <get-customers-for-email-fn
                              <create-customer-fn
                              stripe-customer
                              products
                              prices
                              product
                              price])
