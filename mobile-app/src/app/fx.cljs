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

(defn <get-feed [url]
  (go (-> url
          http/get
          <!
          :body
          (->> (j/call rss :parse))
          <p!)))

(defn dispatch-update-feed [id feed]

  (tap> {:itunes  (-> feed (j/get-in [:itunes :image]))
         :regular (-> feed (j/get-in [:image :url]))})

  (>evt [:update-feed {:feed/id        id
                       :feed/title     (-> feed (j/get :title))
                       :feed/image-url (or (-> feed (j/get-in [:itunes :image]))
                                           (-> feed (j/get-in [:image :url])))
                       :feed/items     (-> feed
                                           (j/get :items)
                                           (->> (mapv (fn [item]
                                                        ;; TODO justin 2021-03-14 use more of the itunes properties
                                                        (j/let [^:js {:keys [id
                                                                             title
                                                                             imageUrl
                                                                             description
                                                                             itunes]} item
                                                                ^:js {:keys [image]} itunes]
                                                          {:feed-item/id          id
                                                           :feed-item/title       title
                                                           :feed-item/image-url   (or image imageUrl)
                                                           :feed-item/description description})))))}]))

(defn <refresh-feed [{:feed/keys [id url]}]
  (tap> {:location "refresh single feed"
         :feed-id  id
         :feed-url url})
  (go
    (let [feed (<! (<get-feed url))]
      (dispatch-update-feed id feed))))
(reg-fx :refresh-feed <refresh-feed)

(reg-fx :refresh-feeds
        (fn [feeds]
          (tap> {:location "refresh all feeds"
                 :feeds    feeds})
          (doall (->> feeds (map <refresh-feed)))))
