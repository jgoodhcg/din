(ns din.app.screen.settings
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as paper]

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

           [:> rn/View {:style (tw "flex flex-1 px-8 w-full")}
            [:> paper/Button {:mode     "outlined"
                              :icon     "account"
                              :on-press #(>evt [:event/navigate :screen/account])}
             "Account"]

            ]
           ]]))]))
