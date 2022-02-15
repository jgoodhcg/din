(ns app.screen.settings
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as paper]

   [reagent.core :as r]
   [potpuri.core :as p]

   [app.helpers :refer [<sub >evt tw]]))

(defn root [props]
  (r/as-element
    [(fn []
       (let [{username :roam-credentials/username
              password :roam-credentials/password} (<sub [:sub/roam-credentials])]

         [:> rn/SafeAreaView {:style (tw "flex flex-1")}
          [:> rn/StatusBar {:visibility "hidden"}]
          [:> paper/Surface {:style (tw "flex flex-1 justify-start pt-8")}

           [:> rn/View {:style (tw "flex flex-1 px-8 w-full")}
            [:> paper/Button {:mode     "outlined"
                              :icon     "account"
                              :on-press #(>evt [:event/go-to-subscription])}
             "Subscription"]

            [:> paper/Divider {:style (tw "w-full my-8")}]

            [:> paper/Subheading "Roam Config"]

            [:> paper/TextInput {:style          (tw "my-2")
                                 :mode           "outlined"
                                 :label          "username"
                                 :default-value  username
                                 :on-change-text #(>evt [:event/update-roam-username %])}]

            [:> paper/TextInput {:style           (tw "my-2")
                                 :mode            "outlined"
                                 :label           "password"
                                 :secureTextEntry true
                                 :default-value   password
                                 :on-change-text  #(>evt [:event/update-roam-password %])}]

            [:> paper/Paragraph (str "These credentials are only stored on the device with Expo SecureStore."
                                     " They are sent to a server (https) to use in a headless chrome instance to obtain only page titles."
                                     " The page titles and credentials are never persisted anywhere but this device."
                                     " Emptying these text inputs will remove the credentials from this device.")]
            ]
           ]]))]))
