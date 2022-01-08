(ns stripe.id
  "Given an email get the associated stripe customer id. Creates the customer if it doesn't exist."
  (:require
   ["stripe" :as stripe-construct]
   [applied-science.js-interop :as j]
   [cljs.core.async :refer [go <! go-loop]]
   [cljs.core.async.interop :refer [<p!]]
   [common.misc :refer [log log-debug log-error error-msg get-envvar]]
   [potpuri.core :as p]))

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

(comment
  (handler {:body (->  {:sub   "xxx"
                        :email "xxx"}
                       clj->js
                       js/JSON.stringify)}
           nil
           #(tap> %2))
  )

(comment
  (tap> "test")

  ;; TODO this needs to come from envvars

  (do
    (def stripe (stripe-construct "sk_test_4eC39HqLyjWDarjtT1zdp7dc"))
    (def sub "45c371ee-a4a5-4a2f-aa82-b3434a7371ad")
    (def email "jgoodhcg+bbtest1@gmail.com"))

  (go
    (-> stripe
        (j/get :customers)
        (j/call :list (j/lit {}))
        <p!
        (js->clj :keywordize-keys true)
        ;; customers are sorted by create date
        ;; :data
        ;; (->> (mapv #(select-keys % [:metadata :id :email :name])))
        tap>
        ))
  ;; if the customer doesn't exist then make it
  (go
    (-> stripe
        (j/get :customers)
        (j/call :create (j/lit {:email    email
                                :metadata {:sub sub}}))
        <p!
        (js->clj :keywordize-keys true)
        ;; :data
        ;; TODO This is where we search through the list of objects looking for one with (get-in [:metadata :sub]) that is the sub from params
        tap>
        ))

  (go (-> (p/map-of stripe email) <get-customers <! tap>))
  (go (-> (p/map-of stripe email sub) <create-customer <! tap>))

  (try
    (tap> "in try")
    (go
      (tap> "in go")
      (let [first-customer-batch (<! (<get-customers (p/map-of stripe email)))]
        (tap> (p/map-of first-customer-batch :first-let))
        (go-loop [customer-batch first-customer-batch]
          (tap> "in go-loop")
          (let [matching-customer (->> customer-batch
                                       :data
                                       (some #(when (= sub (get-in % [:metadata :sub])) %)))]
            (tap> (p/map-of matching-customer))
            (if (some? matching-customer)
              (tap> matching-customer)
              (if (or (-> customer-batch :has_more not) (empty? customer-batch))
                (do
                  (tap> "creating customer")
                  (tap> (<! (<create-customer (p/map-of stripe email sub)))))
                (recur (<! (<get-customers (merge (p/map-of stripe email)
                                                  {:starting_after (-> customer-batch last :id)}))))))))))
    (catch js/Error err
      (log "caught error")
      ;; (log (ex-cause err))
      ;; (callback nil (clj->js {:statusCode 500 :body error-msg}))
      ))

  (let [fcb [10 20 30]]
    (loop [cb fcb]
      (let [mc (->> cb (some #(when (= 5 %) %)))]
        (if (some? mc)
          (str "found it " mc)
          (if (-> cb last (< 5))
            (recur (->> cb (map inc)))
            (throw (str "couldn't find it " cb)))))))

  (tap> (.-env js/process))
  (-> js/process (j/get :env) (j/get :STRIPE_KEY) tap>)

  )
;; => nil
