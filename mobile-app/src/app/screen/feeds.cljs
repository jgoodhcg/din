(ns app.screen.feeds
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as paper]
   ["react-native-gesture-handler" :as g]

   [applied-science.js-interop :as j]
   [reagent.core :as r]
   [potpuri.core :as p]

   [app.helpers :refer [<sub >evt tw]]))

(def feed-input (atom nil))

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
    [:> paper/Surface {:style (tw "p-4")}

     [:> paper/Text (str "Are you sure you want to remove " feed-remove-title "?")]
     [:> paper/Button {:style    (tw "m-4")
                       :mode     "contained"
                       :color    "red"
                       :on-press #(do
                                    (>evt [:modal-close-feed-remove])
                                    (>evt [:remove-feed feed-remove-id])) } "remove"]
     [:> paper/Button {:style    (tw "m-4")
                       :on-press #(>evt [:modal-close-feed-remove])} "cancel"]]]])

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
          [:> paper/Surface {:style (tw "flex flex-1 justify-start")}

           [:> rn/View {:style (tw "flex flex-1 justify-start")}
            (for [{:feed/keys [id image-url title]} feeds]
              [:> rn/View {:key id}
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
                                                             :height (-> width (/ 3))})}]]]
                ]])

            [:> paper/FAB {:style    (tw "absolute right-0 bottom-0 mr-8 mb-20")
                           :icon     (if feed-add-visible "close" "plus")
                           :on-press #(>evt [:modal-toggle-feed-add])}]

            [modal-add (p/map-of feed-add-visible)]
            [modal-remove (p/map-of feed-remove-id feed-remove-title)]

            ]]]))]))
