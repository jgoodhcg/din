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
       (let [{:feed/keys [title image-url items]} (<sub [:selected-feed])]

         [:> rn/SafeAreaView {:style (tw "flex flex-1")}
          [:> rn/StatusBar {:visibility "hidden"}]
          [:> paper/Surface {:style (tw "flex flex-1 justify-start")}

           [:> paper/Card {:style (tw "w-full")}
            [:> paper/Card.Cover {:source {:uri image-url}}]
            [:> paper/Card.Title {:title title}]]

           [:> rn/FlatList
            {:data          (j/lit items)
             :key-extractor (fn [item] (-> item (j/get :id)))
             :render-item   (fn [obj]
                              (j/let [^:js {:keys [id title image-url]} (j/get obj :item)]
                                (r/as-element
                                  [:> rn/View {:key   id
                                               :style (tw "mt-2 pl-2 w-9/12 flex flex-row")}
                                   [:> paper/Card.Cover {:source {:uri image-url}
                                                         :style  (tw "w-20 h-20 mr-2")}]
                                   [:> rn/View {:style (tw "flex flex-col")}
                                    [:> paper/Text {:style (tw "pr-2")} title]
                                    [:> paper/Text {:style (tw "text-gray-500")} "timeline here"]]                                   ]))
                              )}]
           ]]))]))
