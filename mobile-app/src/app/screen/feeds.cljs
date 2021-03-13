(ns app.screen.feeds
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as paper]

   [applied-science.js-interop :as j]
   [reagent.core :as r]
   [potpuri.core :as p]

   [app.helpers :refer [<sub >evt tw]]))

(def feed-input (atom nil))

(defn modal [{:keys [feed-add-visible]}]
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

(defn root [props]
  (r/as-element
    [(fn []
       (let [theme-selection  (<sub [:theme])
             theme            (-> props (j/get :theme))
             feeds            (<sub [:feeds])
             feed-add-visible (<sub [:modal-feed-add-visible])
             ]

         [:> rn/SafeAreaView {:style (tw "flex flex-1")}
          [:> rn/StatusBar {:visibility "hidden"}]
          [:> paper/Surface {:style (tw "flex flex-1 justify-start")}
           [:> rn/View {:style (tw "flex flex-1 justify-start")}
            (for [{:feed/keys [url id]} feeds]
              [:> paper/Text {:key id} url])
            [:> paper/FAB {:style    (tw "absolute right-0 bottom-0 mr-8 mb-20")
                           :icon     (if feed-add-visible "close" "plus")
                           :on-press #(>evt [:modal-toggle-feed-add])}]

            [modal (p/map-of feed-add-visible)]

            ]]]))]))
