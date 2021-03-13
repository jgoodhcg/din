(ns app.fx
  (:require
   ["react-native-rss-parser" :as rss]

   [re-frame.core :refer [reg-fx]]
   [applied-science.js-interop :as j]
   [cljs.core.async :refer [go <!]]
   [cljs.core.async.interop :refer [<p!]]
   [cljs-http.client :as http]

   [app.helpers :refer [>evt]]
   ))

(reg-fx :some-fx-example
        (fn [x]
          (tap> x)
          (println x)))

(reg-fx :refresh-feed
        (fn [{:feed/keys [id url]}]
          (tap> {:location "fx"
                 :feed-id  id
                 :feed-url url})
          (go (-> url
                  http/get
                  <!
                  :body
                  (->> (j/call rss :parse))
                  <p!
                  (#(>evt [:update-feed {:feed/id         id
                                         :feed/parsed-rss %}]))))))
