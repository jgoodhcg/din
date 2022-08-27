(ns din.app.screen.sign-in
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as paper]

   [reagent.core :as r]
   [potpuri.core :as p]

   [app.helpers :refer [<sub >evt tw]]))

(defn root [props]
  (r/as-element
    [(fn []
       (let [{email    :supabase/email
              password :supabase/password} (<sub [:sub/supabase-credentials])
             error                         (<sub [:sub/supabase-sign-in-error])]

         [:> rn/SafeAreaView {:style (tw "flex flex-1")}
          [:> rn/StatusBar {:visibility "hidden"}]
          [:> paper/Surface {:style (tw "flex flex-1 justify-start pt-1")}

           [:> rn/View {:style (tw "flex flex-1 px-8 w-full")}
            [:> paper/TextInput {:style          (tw "mb-4 mt-1")
                                 :mode           "outlined"
                                 :error          (some? error)
                                 :label          "email"
                                 :default-value  email
                                 :on-change-text #(>evt [:event/set-supabase-email %])}]
            [:> paper/TextInput {:style           (tw "mb-4")
                                 :secureTextEntry true
                                 :error           (some? error)
                                 :mode            "outlined"
                                 :label           "password"
                                 :default-value   password
                                 :on-change-text  #(>evt [:event/set-supabase-password %])}]

            (when (some? error)
              [:> paper/Text {:style (tw "text-red-400")} error])

            [:> paper/Button {:style    (tw "my-2")
                              :mode     "contained"
                              :icon     "account"
                              :on-press #(>evt [:event/sign-in])}
             "Sign In"]

            ]
           ]]))]))
