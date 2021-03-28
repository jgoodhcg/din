(ns app.screen.feed-item
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as paper]
   ["react-native-gesture-handler" :as g]

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
       (let [[{feed-title :feed/title}
              {:feed-item/keys [title image-url]}] (<sub [:sub/selected-feed-item])]

         [:> rn/SafeAreaView {:style (tw "flex flex-1")}
          [:> rn/StatusBar {:visibility "hidden"}]
          [:> paper/Surface {:style (tw "flex flex-1 justify-start")}

           [:> rn/View {:style (tw "flex flex-row flex-nowrap p-2")}
            ;; image
            [:> paper/Card.Cover {:source {:uri image-url}
                                  :style  (tw "h-28 w-28")}]

            ;; title
            [:> rn/View {:style (tw "flex flex-col ml-2 mr-32")}
             [:> paper/Title feed-title]
             [:> paper/Text title]]]

           ]]))]))
