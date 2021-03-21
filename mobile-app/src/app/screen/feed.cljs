(ns app.screen.feed
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as paper]
   ["react-native-gesture-handler" :as g]
   ["../../aws-exports" :default aws-config]

   ["expo-file-system" :as fs]
   [cljs.core.async :refer [go <!]]
   [cljs.core.async.interop :refer [<p!]]
   [clojure.edn :as edn]

   [applied-science.js-interop :as j]
   [reagent.core :as r]
   [potpuri.core :as p]

   [app.helpers :refer [<sub >evt tw]]))

(defn root [props]
  (r/as-element
    [(fn []
       (let [;; theme-selection   (<sub [:theme])
             ;; theme             (-> props (j/get :theme))
             ;; feeds             (<sub [:feeds])
             ;; feed-add-visible  (<sub [:modal-feed-add-visible])
             ;; feed-remove-id    (<sub [:modal-feed-remove-id])
             ;; feed-remove-title (<sub [:modal-feed-remove-title])
             ;; width             (-> rn/Dimensions (j/call :get "window") (j/get :width))
             selected-feed (<sub [:selected-feed])
             ]

         [:> rn/SafeAreaView {:style (tw "flex flex-1")}
          [:> rn/StatusBar {:visibility "hidden"}]
          [:> paper/Surface {:style (tw "flex flex-1 justify-start")}
           [:> rn/ScrollView {:content-container-style (tw "flex justify-start flex-row flex-wrap")}

            [:> paper/Text (:feed/title selected-feed)]

            ]]]))]))
