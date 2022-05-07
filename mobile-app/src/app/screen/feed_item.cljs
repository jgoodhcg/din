(ns app.screen.feed-item
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as paper]
   ["react-native-gesture-handler" :as g]
   ["react-native-controlled-mentions" :as cm]

   [applied-science.js-interop :as j]
   [reagent.core :as r]
   [potpuri.core :as p]

   [app.helpers :refer [<sub >evt >evt-sync tw]]

   [clojure.string :as s]))

(defn page-suggestions [pages e]
  (let [on-suggest  (-> e (j/get :onSuggestionPress))
        maybe-page  (-> e (j/get :keyword))
        suggestions (when (and (-> maybe-page count (> 0))
                               (-> pages count (> 0)))
                      (->> pages
                           (filter #(re-find (re-pattern (str "(?i)" maybe-page)) %))))  ]
    (when (some? maybe-page)
      (r/as-element
        [(fn []
           [:> rn/View
            (for [title suggestions]
              [:> paper/List.Item
               {:key      (str (random-uuid))
                :title    title
                :on-press #(on-suggest #js {:id (str (random-uuid)) :name title})}])])]))))

(def my-ref (atom nil))

(comment
  (-> @my-ref
      ;; js/Object.keys ;; => #js ["_nativeTag" "_children" "viewConfig" "_internalFiberInstanceHandleDEV" "clear" "isFocused" "getNativeRef"]
      (j/get :viewConfig)
      (j/get :NativeProps)
      (j/get :selection)
      )
    )

(comment
  (-> "a [[]] b ] [c [["
      ((fn [text]
         (loop [from 0
                indexes []]
           (let [i (s/index-of text "[[" from)]
             (if (nil? i)
               indexes
               (recur (-> i (+ 1)) (-> indexes (conj i)))))
           ))))
  )
(defn my-text-input [{:keys [selected-note]}]
  (let [pages (<sub [:sub/roam-pages])]
    ;; [:> cm/MentionInput
    ;;  {:style                  (tw "text-gray-50")
    ;;   :text-align             "left"
    ;;   :text-align-vertical    "top"
    ;;   :multi-line             true
    ;;   :number-of-lines        10
    ;;   :placeholder            "Make note here"
    ;;   :placeholder-text-color (:color (tw "text-gray-500"))
    ;;   :value                  (:feed-item-note/text selected-note)
    ;;   :on-change              #(>evt-sync [:event/update-selected-note-text
    ;;                                        {:feed-item-note/text %}])

    ;;   :part-types
    ;;   [{:trigger                   "#"
    ;;     :getPlainString            #(-> % (j/get :name) ((fn [s] (str "[[" s "]]"))))
    ;;     :isInsertSpaceAfterMention true
    ;;     :textStyle                 (tw "text-blue-400")
    ;;     :renderSuggestions         (partial page-suggestions pages)}]}]
    [:> rn/View
     [:> rn/TextInput {:onSelectionChange
                       #(>evt [:event/set-note-selection
                               (let [selection (-> %
                                                   (j/get :nativeEvent)
                                                   (j/get :selection))
                                     start     (-> selection (j/get :start))
                                     end       (-> selection (j/get :end))]
                                 {:note-selection/start start
                                  :note-selection/end   end})])
                       :ref #(reset! my-ref %)}]

     ]
    ))

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
    (for [{left :feed-item-note/left
           id   :feed-item-note/id
           :as  note} notes]
      [:> rn/View {:key   id
                   :style (merge {:left left} (tw "absolute w-1 h-4 bg-gray-200"))}])

    ;; selected note
    (when-some [{left :feed-item-note/left} selected-note]
      [:> rn/View {:style (merge {:left left} (tw "absolute -top-1 w-1 h-12 bg-yellow-400 rounded-t"))}])

    (when (some? selected-note)
      [:> rn/View {:style (tw "absolute left-0 top-11 w-full h-60 bg-gray-700 border-4 border-yellow-400")}
       [:> rn/View {:style (tw "p-4")}
        [my-text-input (p/map-of selected-note)]]])

    (when (some? selected-note)
      [:> rn/View {:style (tw "absolute right-2 bottom-8")}
       [:> rn/View {:style (tw "flex flex-row")}
        [:> paper/IconButton {:icon     "delete"
                              :style    (tw "mr-32")
                              :on-press #(>evt [:event/delete-selected-note])}]
        [:> paper/IconButton {:icon     "arrow-left"
                              :on-press #(>evt [:event/cycle-selected-note
                                                {:cycle/direction :cycle/prev}])}]
        [:> paper/IconButton {:icon     "arrow-right"
                              :on-press #(>evt [:event/cycle-selected-note
                                                {:cycle/direction :cycle/next}])}]
        [:> paper/IconButton {:icon     "share"
                              :on-press #(>evt [:event/share-selected-note])
                              :size     24}]]])]])

(defn rate-menu-item [{:keys [feed-id rate]}]
  [:> paper/Menu.Item
   {:title    (str rate "x")
    :on-press #(do
                 (tap> {:location :on-press
                        :rate     rate})
                 (>evt [:event/set-playback-rate {:feed/id            feed-id
                                                  :feed/playback-rate rate}])
                 (>evt [:event/set-playback-rate-menu-visible false]))}])

(defn root [props]
  (r/as-element
    [(fn []
       (let [[{feed-title    :feed/title
               feed-id       :feed/id
               playback-rate :feed/playback-rate
               :or           {playback-rate 1}}
              {:feed-item/keys [title
                                id
                                image-url
                                progress-width
                                ;; started ;; what is this for again?
                                duration-str
                                duration
                                position-str
                                playback-status
                                notes
                                position
                                selected-note]}] (<sub [:sub/selected-feed-item])
             playback-rate-menu-visible          (<sub [:sub/playback-rate-menu-visible])
             show-add-page-button                (<sub [:sub/display-add-page-button])]


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

            (when (not= playback-status :status/loading)
              [progress-bar (p/map-of
                              progress-width
                              duration-str
                              position-str
                              notes
                              selected-note)])

            ;; controlls
            (if (= playback-status :status/loading)
              [:> paper/ActivityIndicator {:animating true :size 42}]

              [:> rn/View {:style (tw "flex flex-row justify-between items-center px-4 h-32")}
               [:> paper/IconButton {:icon     "skip-backward"
                                     :on-press #(>evt-sync [:event/seek-selected-item {:seek/absolute-position 0}])}]
               [:> paper/IconButton {:icon     "rewind-30"
                                     :on-press #(>evt-sync [:event/seek-selected-item {:seek/offset-millis -30000}])}]
               (case playback-status
                 (:status/stopped :status/paused) [:> paper/IconButton
                                                   {:icon     "play" :size 42
                                                    :on-press #(>evt-sync [:event/play-selected-item])}]

                 :status/playing [:> paper/IconButton
                                  {:icon     "pause" :size 42
                                   :on-press #(>evt-sync [:event/pause-selected-item])}]
                 nil)
               [:> paper/IconButton {:icon     "fast-forward-30"
                                     :on-press #(>evt-sync [:event/seek-selected-item {:seek/offset-millis 30000}])}]
               [:> paper/IconButton {:icon     "skip-forward"
                                     :on-press #(>evt-sync [:event/seek-selected-item {:seek/absolute-position duration}])}]])

            ;; add note and playback rate
            (when (not= playback-status :status/loading)
              [:> rn/View {:style (tw "flex flex-row justify-between mt-4 p-2")}

               ;; playback rate
               [:> paper/Menu {:visible    playback-rate-menu-visible
                               :on-dismiss #(>evt [:event/set-playback-rate-menu-visible false])
                               :anchor
                               (r/as-element
                                [:> paper/Button
                                 {:mode     "text" :icon "play-speed"
                                  :color    (-> props (j/get :theme) (j/get :colors) (j/get :text))
                                  :on-press #(>evt [:event/set-playback-rate-menu-visible true])}
                                 (str playback-rate "x")])}
                [rate-menu-item {:feed-id feed-id :rate 0.50}]
                [rate-menu-item {:feed-id feed-id :rate 0.75}]
                [rate-menu-item {:feed-id feed-id :rate 1.00}]
                [rate-menu-item {:feed-id feed-id :rate 1.25}]
                [rate-menu-item {:feed-id feed-id :rate 1.50}]
                [rate-menu-item {:feed-id feed-id :rate 1.75}]
                [rate-menu-item {:feed-id feed-id :rate 2.00}]
                ]


               ;; add note
               [:> paper/Button {:mode     "contained" :icon "note"
                                 :on-press #(>evt-sync [:event/add-note {:feed/id                 feed-id
                                                                         :feed-item/id            id
                                                                         :feed-item-note/position position
                                                                         :feed-item-note/text     ""}])}
                "Add note"]])]]

          (when show-add-page-button
            [:> rn/KeyboardAvoidingView {:style {}}
             [:> paper/Button {:mode "contained"} "[[  ]]"]])

          ]))]))
