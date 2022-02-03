(ns app.screen.payment
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as paper]
   ["@stripe/stripe-react-native" :as stripe-rn]

   [applied-science.js-interop :as j]
   [reagent.core :as r]
   [potpuri.core :as p]
   [cljs.core.async :refer [go <!]]
   [cljs.core.async.interop :refer [<p!]]

   [app.helpers :refer [<sub >evt tw]]))

(defonce billing-details (atom nil))

(defn root [props]
  (r/as-element
    [(fn []
       (let []

         [:> rn/SafeAreaView {:style (tw "flex flex-1")}
          [:> rn/StatusBar {:visibility "hidden"}]
          [:> paper/Surface {:style (tw "flex flex-1 justify-start pt-8")}

           [:> paper/Headline "Add payment method"]
           [:> stripe-rn/CardField {:style (tw "w-full h-12 my-4")
                                    :on-card-change #(do
                                                       (->> % (reset! billing-details))
                                                       (println %))}]
           [:> paper/Button {:mode "contained"} "submit"]

           ]]))]))

(comment
  (go
    (-> stripe-rn
        (j/call :confirmSetupIntent
                "seti_1KK0fhBAaAf4dYG6wVLXIo8J_secret_L00hw9TsaKPQS8BD4zZ17DFFTbUYr8P"
                (j/lit {:type "Card"
                        :billingDetails @billing-details}))
        <p!
        tap>
        ))
  (-> billing-details tap>)
  (tap> "test")
  )
