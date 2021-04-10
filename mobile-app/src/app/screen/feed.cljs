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
    (for [note notes] ;; notes are js objects because of flatlist
      [:> rn/View {:key   (random-uuid)
                   :style (merge {:left (j/get note :left)} (tw "absolute w-1 h-4 bg-gray-200"))}])]])

(defn root [props]
  (r/as-element
    [(fn []
       (let [{:feed/keys [title
                          image-url
                          items
                          item-sort item-sort-icon
                          item-filter item-filter-icon]
              feed-id    :feed/id} (<sub [:sub/selected-feed])
             opposite-sort         (if (= :item-sort/ascending item-sort)
                                     :item-sort/descending
                                     :item-sort/ascending)
             opposite-filter       (if (= :item-filter/finished item-filter)
                                     nil
                                     :item-filter/finished)]

         [:> rn/SafeAreaView {:style (tw "flex flex-1")}
          [:> rn/StatusBar {:visibility "hidden"}]
          [:> paper/Surface {:style (tw "flex flex-1 justify-start")}

           [:> paper/Card {:style (tw "w-full")}
            [:> paper/Card.Cover {:source {:uri image-url}}]
            [:> paper/Card.Title {:title title}]
            [:> paper/Card.Actions
             [:> paper/IconButton {:icon     "checkbox-multiple-blank-outline"
                                   :on-press #(>evt [:event/set-finished-override-all-items
                                                     {:feed/id feed-id
                                                      :feed-item/finished-override
                                                      :user-override/unfinished}])}]

             [:> paper/IconButton {:icon     "checkbox-multiple-marked"
                                   :on-press #(>evt [:event/set-finished-override-all-items
                                                     {:feed/id feed-id
                                                      :feed-item/finished-override
                                                      :user-override/finished}])}]

             [:> paper/IconButton {:icon     item-sort-icon
                                   :on-press #(>evt [:event/set-feed-item-sort
                                                     {:feed/id        feed-id
                                                      :feed/item-sort opposite-sort}])}]

             [:> paper/IconButton {:icon     item-filter-icon
                                   :on-press #(>evt [:event/set-feed-item-filter
                                                     {:feed/id          feed-id
                                                      :feed/item-filter opposite-filter}])}]]]

           [:> rn/FlatList
            {:data          (j/lit items)
             :key-extractor (fn [item] (-> item (j/get :id)))
             :render-item
             (fn [obj]
               (j/let [^:js {:keys [id
                                    title
                                    finished-override
                                    image-url
                                    progress-width
                                    notes
                                    started]} (j/get obj :item)]
                 (r/as-element
                   [:> g/LongPressGestureHandler
                    {:on-handler-state-change
                     (fn [e]
                       (let [state (-> e (j/get-in [:nativeEvent :state]))]
                         (when (= (j/get g/State :ACTIVE)
                                  state)
                           (tap> {:location "long press on item"}))))}

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
                       [:> paper/Text {:style (tw "pr-2")} finished-override]
                       (when started
                         [progress-bar
                          (p/map-of progress-width
                                    notes)])]]]])))}]]]))]))
