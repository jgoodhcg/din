(ns app.screen.feed-item
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as paper]
   ["react-native-gesture-handler" :as g]
   ["react-native-controlled-mentions" :as cm]

   [applied-science.js-interop :as j]
   [reagent.core :as r]
   [potpuri.core :as p]

   [app.helpers :refer [<sub >evt >evt-sync tw]]))

(defn progress-bar [{:keys [progress-width duration-str position-str notes selected-note]}]
  [:> rn/View {:style (tw "mt-2 px-2 h-80")}
   [:> rn/View {:style (tw "h-full w-full")}
    ;; progress bar
    [:> rn/View {:style (tw "absolute left-0 w-full h-4 bg-purple-400 opacity-50 rounded") }]
    [:> rn/View {:style (merge {:width progress-width}
                               (tw "absolute left-0 h-4 bg-purple-400 rounded-l"))}]
    [:> rn/View {:style (tw "absolute right-0 top-4")}
     [:> paper/Text {:style (tw "text-gray-400")} duration-str]]
    [:> rn/View {:style (tw "absolute left-0 top-4")}
     [:> paper/Text {:style (tw "text-gray-400")} position-str]]

    ;; notes
    (for [{:keys [left]} notes]
      [:> rn/View {:key   (random-uuid)
                   :style (merge {:left left} (tw "absolute w-1 h-4 bg-gray-200"))}])

    ;; selected note
    (when-some [{:keys [left]} selected-note]
      [:> rn/View {:style (merge {:left left} (tw "absolute -top-1 w-1 h-12 bg-yellow-400 rounded-t"))}])

    (when (some? selected-note)
      [:> rn/View {:style (tw "absolute left-0 top-11 w-full h-64 bg-gray-700 border-4 border-yellow-400 rounded")}
       [:> rn/View {:style (tw "p-4")}
        [:> paper/Text "my text input goes here"]
        ;; [my-text-input] ;; TODO
        ]])

    (when (some? selected-note)
      [:> rn/View {:style (tw "absolute right-2 bottom-6")}
       [:> paper/IconButton {:icon     "share"
                             :on-press #(>evt [:on-share])
                             :size     24}]])]])

(defn page-suggestions [e]
  (let [pages       [] ;; TODO sub to pages
        on-suggest  (-> e (j/get :onSuggestionPress))
        maybe-page  (-> e (j/get :keyword))
        suggestions (when (-> maybe-page count (> 0))
                      (->> pages
                           (filter #(re-find (re-pattern (str "(?i)" maybe-page)) %))))]
    (when (some? maybe-page)
      (r/as-element
        [(fn []
           [:> rn/View
            (for [title suggestions]
              [:> paper/List.Item
               {:key      (str (random-uuid))
                :title    title
                :on-press #(on-suggest #js {:id (str (random-uuid)) :name title})}])])]))))

(defn my-text-input []
  (let [{:keys [selected-index selected-note]}
        {:selected-note {:text "test note"}} ;; TODO sub to notes
        ]
    [:> cm/MentionInput
     {:style                  (tw "text-gray-50")
      :text-align             "left"
      :text-align-vertical    "top"
      :multi-line             true
      :number-of-lines        10
      :placeholder            "Make note here"
      :placeholder-text-color (:color (tw "text-gray-500"))
      :value                  (:text selected-note)
      :on-change              #() ;; (swap! notes-atom assoc-in [:notes selected-index :text] %)

      :part-types
      [{:trigger                   "#"
        :getPlainString            #(-> % (j/get :name) ((fn [s] (str "[[" s "]]"))))
        :isInsertSpaceAfterMention true
        :textStyle                 (tw "text-blue-400")
        :renderSuggestions         page-suggestions}]}]))

(defn root [props]
  (r/as-element
    [(fn []
       (let [[{feed-title :feed/title}
              {:feed-item/keys [title
                                image-url
                                progress-width
                                ;; started ;; what is this for again?
                                duration-str
                                position-str
                                playback-status]}] (<sub [:sub/selected-feed-item])

             ;; TODO
             notes         []
             selected-note {:left "10%"}]

         [:> rn/SafeAreaView {:style (tw "flex flex-1")}
          [:> rn/StatusBar {:visibility "hidden"}]
          [:> paper/Surface {:style (tw "flex flex-1 justify-start")}
           [:> rn/View {:style (tw "flex flex-1 justify-start")}
            [:> rn/View {:style (tw "flex flex-row flex-nowrap p-2")}
             ;; image
             [:> paper/Card.Cover {:source {:uri image-url}
                                   :style  (tw "h-28 w-28")}]

             ;; title
             [:> rn/View {:style (tw "flex flex-col ml-2 mr-32")}
              [:> paper/Title feed-title]
              [:> paper/Text title]]]

            [progress-bar (p/map-of progress-width duration-str position-str notes selected-note)]

            ;; notes
            (for [{:keys [left]} notes]
              [:> rn/View {:key   (random-uuid)
                           :style (merge {:left left} (tw "absolute w-1 h-4 bg-gray-200 "))}])

            ;; selected note
            (when-some [{:keys [left]} selected-note]
              [:> rn/View {:style (merge {:left left} (tw "absolute -top-1 w-1 h-12 bg-yellow-400 rounded-t"))}])

            (when (some? selected-note)
              [:> rn/View {:style (tw "absolute left-0 top-11 w-full h-64 bg-gray-700 border-4 border-yellow-400 rounded")}
               [:> rn/View {:style (tw "p-4")}
                [my-text-input]]])

            (when (some? selected-note)
              [:> rn/View {:style (tw "absolute right-2 bottom-6")}
               [:> paper/IconButton {:icon "share" :on-press #() ;; TODO move on-share from rss-play to fx
                                     :size 24}]])

            ;; controlls
            [:> rn/View {:style (tw "flex flex-row justify-between items-center px-4 h-32")}
             [:> paper/IconButton {:icon     "arrow-left"
                                   :on-press #() ;;on-prev-note
                                   }]
             [:> paper/IconButton {:icon "rewind" :disabled true}]
             [:> paper/IconButton {:icon     "rewind-30"
                                   :on-press #() ;;on-backward-30
                                   }]

             (case playback-status
               (:status/stopped :status/paused) [:> paper/IconButton
                                                 {:icon     "play" :size 42
                                                  :on-press #(>evt-sync [:event/play-selected-item])}]

               :status/playing [:> paper/IconButton
                                {:icon     "pause" :size 42
                                 :on-press #(>evt-sync [:event/pause-selected-item])}]

               ;; :status/loading
               [:> paper/ActivityIndicator {:animating true :size 42}])

             [:> paper/IconButton {:icon     "fast-forward-30"
                                   :on-press #(>evt [:on-forward-30])
                                   }]
             [:> paper/IconButton {:icon "fast-forward" :disabled true}]
             [:> paper/IconButton {:icon     "arrow-right"
                                   :on-press #(>evt [:on-next-note])
                                   }]]

            ;; add note
            [:> rn/View {:style (tw "flex flex-row justify-end mt-4 p-2")}
             [:> paper/Button {:mode     "contained" :icon "note"
                               :on-press #(>evt [:on-add-note])
                               } "Add note"]]


            ]

           ]]))]))
