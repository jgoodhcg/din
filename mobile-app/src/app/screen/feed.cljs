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

(defn progress-bar [{:keys [progress-width notes]}]
  [:> rn/View {:style (tw "mt-2 px-2 h-8")}
   [:> rn/View {:style (tw "h-full w-full")}

    ;; progress bar
    [:> rn/View {:style (tw "absolute left-0 w-full h-4 bg-purple-400 opacity-50 rounded") }]
    [:> rn/View {:style (merge {:width progress-width}
                               (tw "absolute left-0 h-4 bg-purple-400 rounded-l"))}]

    ;; notes
    (for [{:keys [left]} notes]
      [:> rn/View {:key   (random-uuid)
                   :style (merge {:left left} (tw "absolute w-1 h-4 bg-gray-200"))}])]])

(defn root [props]
  (r/as-element
    [(fn []
       (let [{:feed/keys [title image-url items]
              feed-id    :feed/id} (<sub [:sub/selected-feed])]

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
                              (j/let [^:js {:keys [id title image-url]} (j/get obj :item)
                                      progress-width (str (rand-int 75) "%")
                                      notes          (->> (range (rand-int 10))
                                                          (map (fn [_] {:left (str (rand-int 75) "%")})))]
                                (r/as-element
                                  [:> g/RectButton {:on-press
                                                    #(>evt [:event/select-feed-item {:feed-item/id id
                                                                                     :feed/id      feed-id
                                                                                     :navigate     true}])}
                                   [:> rn/View {:key   id
                                                :style (tw "mt-2 pl-2 w-9/12 flex flex-row")}
                                    [:> paper/Card.Cover {:source {:uri image-url}
                                                          :style  (tw "w-20 h-20 mr-2")}]
                                    [:> rn/View {:style (tw "flex flex-col w-full justify-center")}
                                     [:> paper/Text {:style (tw "pr-2")} title]
                                     [progress-bar
                                      (p/map-of progress-width
                                                notes)]]]]))
                              )}]
           ]]))]))
