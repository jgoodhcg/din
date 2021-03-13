(ns app.screen.feeds
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as paper]

   [applied-science.js-interop :as j]
   [reagent.core :as r]

   [app.helpers :refer [<sub >evt tw]]))

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
          [:> paper/Surface {:style (tw "flex flex-1 justify-center")}
           [:> rn/View {:style (tw "flex flex-1 justify-start")}
            (for [{url :feeds/url} feeds]
              [:> paper/Text url])
            [:> paper/FAB {:style    (tw "absolute right-0 bottom-0 mr-8 mb-20")
                           :icon     (if feed-add-visible "close" "plus")
                           :on-press #(>evt [:modal-toggle-feed-add])}]
            [:> paper/Text (str feed-add-visible)]
            ]]]))]))
