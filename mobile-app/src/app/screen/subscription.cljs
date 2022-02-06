(ns app.screen.subscription
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as paper]

   [reagent.core :as r]
   [potpuri.core :as p]

   [app.helpers :refer [<sub >evt tw]]))

(defn root [props]
  (r/as-element
    [(fn []
       (let [active-sub    (<sub [:sub/active-subscription-price-id])
             possible-subs (<sub [:sub/possible-subscriptions])]

         [:> rn/SafeAreaView {:style (tw "flex flex-1")}
          [:> rn/StatusBar {:visibility "hidden"}]
          [:> paper/Surface {:style (tw "flex flex-1 justify-start pt-8")}

           [:> rn/View {:style (tw "flex items-center w-full")}
            (for [{id     :stripe.price/id
                   amount :stripe.price/unit-amount
                   name   :stripe.product/name
                   desc   :stripe.product/description} possible-subs]
              ;; TODO 2022-02-06 Justin move this to sub logic
              (let [purchased (= id active-sub)
                    amount (-> amount (/ 100))]
                [:> paper/Card {:mode "outlined" :style (tw "w-72")}
                 [:> paper/Card.Title {:title (str name (when purchased " (purchased)"))}]
                 [:> paper/Card.Content
                  [:> paper/Title (str "$" amount)]
                  [:> paper/Paragraph desc]]
                 [:> paper/Card.Actions
                  [:> paper/Button {:mode     "contained"
                                    :disabled purchased}
                   "Buy"]]]))
            ]

           ]]))]))
