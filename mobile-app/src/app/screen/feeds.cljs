(ns app.screen.feeds
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

(def feed-input (atom nil))

(def dd (-> fs (j/get :documentDirectory)))
(def app-db-file (str dd "app-db.edn"))
(def files (r/atom []))
(def app-db (r/atom {}))
(def app-db-info (r/atom {}))

(defn modal-add [{:keys [feed-add-visible]}]
  [:> paper/Portal
   [:> paper/Modal {:visible                 feed-add-visible
                    :on-dismiss              #(>evt [:modal-toggle-feed-add])
                    :content-container-style (tw "m-4")}
    [:> paper/Surface {:style (tw "p-4")}
     [:> paper/TextInput {:label          "Feed URL"
                          :on-change-text #(reset! feed-input %)}]
     [:> paper/Button {:style    (tw "m-4")
                       :on-press #(do
                                    (>evt [:modal-toggle-feed-add])
                                    (>evt [:add-feed @feed-input])) } "save"]
     [:> paper/Button {:style    (tw "m-4")
                       :on-press #(>evt [:modal-toggle-feed-add])} "cancel"]]]])

(defn modal-remove [{:keys [feed-remove-id feed-remove-title]}]
  [:> paper/Portal
   [:> paper/Modal {:visible                 (some? feed-remove-id)
                    :on-dismiss              #(>evt [:modal-toggle-feed-add])
                    :content-container-style (tw "m-4")}
    [:> paper/Surface {:style (tw "p-4 justify-center")}

     [:> paper/Text {:style (tw "text-center")} "Permanetly remove this feed and notes? "]
     [:> paper/Subheading {:style (tw "text-center text-purple-400")} feed-remove-title]
     [:> paper/Button {:style    (tw "m-4")
                       :mode     "contained"
                       :icon     "delete"
                       :color    "red"
                       :on-press #(do
                                    (>evt [:modal-close-feed-remove])
                                    (>evt [:remove-feed feed-remove-id])) } "remove"]
     [:> paper/Button {:style    (tw "m-4")
                       :on-press #(>evt [:modal-close-feed-remove])} "cancel"]]]])

(defn feed-card [{:keys [id image-url title width]}]
  [:> rn/View {:key id}
   ;; TODO justin 2021-03-14 use title for accessiblity label
   [:> g/LongPressGestureHandler {:on-handler-state-change
                                  (fn [e]
                                    (let [state (-> e (j/get-in [:nativeEvent :state]))]
                                      (when (= (j/get g/State :ACTIVE)
                                               state)
                                        (>evt [:modal-open-feed-remove id]))))}
    [:> rn/View
     [:> paper/Card {:key id}
      [:> paper/Card.Cover {:source      {:uri image-url}
                            :resize-mode "contain"
                            :style       (merge (tw "")
                                                {:width  (-> width (/ 3))
                                                 :height (-> width (/ 3))})}]]]]])

(defn root [props]
  (r/as-element
    [(fn []
       (let [theme-selection   (<sub [:theme])
             theme             (-> props (j/get :theme))
             feeds             (<sub [:feeds])
             feed-add-visible  (<sub [:modal-feed-add-visible])
             feed-remove-id    (<sub [:modal-feed-remove-id])
             feed-remove-title (<sub [:modal-feed-remove-title])
             width             (-> rn/Dimensions (j/call :get "window") (j/get :width))]

         [:> rn/SafeAreaView {:style (tw "flex flex-1")}
          [:> rn/StatusBar {:visibility "hidden"}]
          ;; TODO justin 2021-03-20 Add scroll view
          [:> paper/Surface {:style (tw "flex flex-1 justify-start")}

           [:> rn/View {:style (tw "flex flex-1 justify-start flex-row flex-wrap")}
            (for [{:feed/keys [id image-url title]} feeds]
              [feed-card (merge {:key id}
                                (p/map-of id image-url title width))])

            ;; TODO justin 2021-03-20 replace fab with outline of a podcast at the end of the feeds block with a Plus and "add feed"
            [:> paper/FAB {:style    (tw "absolute right-0 bottom-0 mr-8 mb-20")
                           :icon     (if feed-add-visible "close" "plus")
                           :on-press #(>evt [:modal-toggle-feed-add])}]

            [modal-add    (p/map-of feed-add-visible)]
            [modal-remove (p/map-of feed-remove-id feed-remove-title)]

            [:> rn/View {:style (tw "w-full h-40 p-4")}
             ;;             [:> paper/Text (-> aws-config js->clj str)]
             [:> paper/Text {:style (tw "mt-4")} dd]
             [:> paper/Text {:style (tw "my-4")} app-db-file]
             [:> paper/Button {:mode     "contained"
                               :on-press #(go
                                            (-> fs (j/call :readDirectoryAsync dd)
                                                <p!
                                                ((fn [result] (reset! files (-> result js->clj))))))}
              "get directory"]
             [:> paper/Text {:style (tw "mt-4")} (str @files)]
             [:> paper/Button {:mode     "contained"
                               :on-press #(go
                                            (-> fs (j/call :getInfoAsync app-db-file)
                                                <p!
                                                ((fn [result] (reset! app-db-info (-> result js->clj))))))}
              "get app-db file info"]
             [:> paper/Text {:style (tw "my-4")} (str @app-db-info)]
             [:> paper/Button {:mode     "contained"
                               :on-press #(go
                                            (-> fs (j/call :readAsStringAsync app-db-file)
                                                <p!
                                                ((fn [result] (reset! app-db (-> result edn/read-string))))))}
              "read app-db file"]
             [:> paper/Text {:style (tw "my-4")} (str @app-db)]
             ]
            ]]]))]))
