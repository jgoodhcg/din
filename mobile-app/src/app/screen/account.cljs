(ns app.screen.account
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as paper]

   [reagent.core :as r]
   [potpuri.core :as p]

   [app.helpers :refer [<sub >evt tw]]))

(defn root [props]
  (r/as-element
    [(fn []
       (let [{email :email
              :as user} nil]

         [:> rn/SafeAreaView {:style (tw "flex flex-1")}
          [:> rn/StatusBar {:visibility "hidden"}]
          [:> paper/Surface {:style (tw "flex flex-1 justify-start pt-8")}

           [:> rn/View {:style (tw "flex flex-1 px-8 w-full")}
            (if (some? user)
              [:> rn/View
               [:> paper/Text (str "You are signed in as " email)]
               [:> paper/Button {:mode     "flat"
                                 :icon     "account-cancel-outline"
                                 :on-press #(>evt [:event/navigate :screen/signup])}
                "Logout"]]
              [:> rn/View {:style (tw "flex flex-col h-48 justify-between")}
               [:> paper/Button {                              :mode     "contained"
                              :icon     "account"
                              :on-press #(>evt [:event/navigate :screen/login])}
             "Login"]

            [:> paper/Button {:style    (tw "mb-16")
                              :mode     "outlined"
                              :icon     "account-outline"
                              :on-press #(>evt [:event/navigate :screen/signup])}
             "Signup"]])




            ]
           ]]))]))
