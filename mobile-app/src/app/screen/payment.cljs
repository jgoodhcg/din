(ns app.screen.payment
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as paper]
   ["@stripe/stripe-react-native" :as stripe-rn]

   [applied-science.js-interop :as j]
   [reagent.core :as r]
   [potpuri.core :as p]

   [app.helpers :refer [<sub >evt tw]]))

(defn root [props]
  (r/as-element
    [(fn []
       (let []

         [:> rn/SafeAreaView {:style (tw "flex flex-1")}
          [:> rn/StatusBar {:visibility "hidden"}]
          [:> paper/Surface {:style (tw "flex flex-1 justify-start pt-8")}

           [:> paper/Headline "Add payment method"]
           [:> stripe-rn/CardField {:style (tw "w-full h-12 my-4")}]
           [:> paper/Button {:mode "contained"} "submit"]

           ]]))]))
