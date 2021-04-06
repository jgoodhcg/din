(ns app.fx
  (:require
   ["react-native-rss-parser" :as rss]
   ["react-native" :as rn]
   ["expo-av" :as av]
   ["expo-file-system" :as fs]
   ["expo-constants" :as expo-constants]

   [re-frame.core :refer [reg-fx]]
   [applied-science.js-interop :as j]
   [cljs.core.async :refer [go <!]]
   [cljs.core.async.interop :refer [<p!]]
   [cljs-http.client :as http]
   [clojure.edn :as edn]
   [tick.alpha.api :as t]

   [app.helpers :refer [>evt >evt-sync screen-key-name-mapping]]))

(def dd (-> fs (j/get :documentDirectory)))

(def app-db-file (str dd "app-db.edn"))

(def version (-> expo-constants
                 (j/get :default)
                 (j/get :manifest)
                 (j/get :version)))

(def playback-object (atom (av/Audio.Sound.)))

(defn <get-feed [url]
  (go (-> url
          http/get
          <!
          :body
          (->> (j/call rss :parse))
          <p!)))

(defn dispatch-update-feed [feed-id feed]
  (>evt [:event/update-feed
         {:feed/id                feed-id
          :feed/title             (-> feed (j/get :title))
          :feed/image-url         (or (-> feed (j/get-in [:itunes :image]))
                                      (-> feed (j/get-in [:image :url])))
          :feed/items-not-indexed (-> feed
                                      (j/get :items)
                                      (->> (mapv (fn [item]
                                                   (j/let [^:js {:keys [id
                                                                        title
                                                                        imageUrl
                                                                        description
                                                                        itunes
                                                                        published
                                                                        enclosures]} item
                                                           ^:js {:keys [image order]} itunes
                                                           ^:js {:keys [url length]} (first enclosures)]
                                                     {:feed-item/id          id
                                                      :feed-item/title       title
                                                      :feed-item/image-url   (or image imageUrl)
                                                      :feed-item/description description
                                                      :feed-item/url         url
                                                      :feed-item/published   (-> published
                                                                                 js/Date.parse
                                                                                 (t/new-duration :millis)
                                                                                 t/instant
                                                                                 t/format)
                                                      :feed-item/order       order})))))}]))

(defn <refresh-feed [{:feed/keys [id url]}]
  (go
    (let [feed (<! (<get-feed url))]
      (dispatch-update-feed id feed))))
(reg-fx :effect/refresh-feed <refresh-feed)

(reg-fx :effect/refresh-feeds
        (fn [feeds]
          (doall (->> feeds (map <refresh-feed)))))

(reg-fx :effect/persist
        (fn [app-db-str]
          (-> fs (j/call :writeAsStringAsync
                         app-db-file
                         app-db-str))))

(reg-fx :effect/load
        (fn []
          (println "load fx ----------------------------------------------------")
          (tap> {:location "load fx"})
          (go
            (try
              (-> fs (j/call :getInfoAsync app-db-file)
                  <p!
                  ((fn [info-result]
                     (println "load fx: info results -------------- ")
                     (println (-> info-result (j/get :exists)))
                     (println (-> info-result (j/get :exists) (= true)))
                     (if (-> info-result (j/get :exists) (= false))
                       ;; file does NOT exist
                       (do
                         (-> rn/Alert (j/call :alert "App-db did not exist"))
                         (println "load fx: file does NOT exist -------------------------------")
                         (>evt [:event/set-version version])
                         (>evt [:event/refresh-feeds]))
                       ;; file exists load db
                       (go
                         (try
                           (println "load fx: file exists -------------------------------")
                           (-> fs (j/call :readAsStringAsync app-db-file)
                               <p!
                               edn/read-string
                               (#(>evt [:event/load-app-db {:app-db  %
                                                            :version version}])))
                           (catch js/Object e
                             (-> rn/Alert (j/call :alert "Failure on readAsStringAsync" (str e))))))))))
              (catch js/Object e
                (-> rn/Alert (j/call :alert "Failure on getInfoAsync" (str e))))))))

(def !navigation-ref (clojure.core/atom nil))

(defn navigate [screen-key] ;; no params yet
  ;; TODO implement a check that the navigation component has initialized
  ;; https://reactnavigation.org/docs/navigating-without-navigation-prop#handling-initialization
  ;; The race condition is in my favor if the user has to press a component within the navigation container
  (tap> {:location       "navigate fx"
         :navigation-ref @!navigation-ref
         :screen-key     screen-key
         :screen-name    (get screen-key-name-mapping screen-key)})

  (-> @!navigation-ref
      ;; no params yet for second arg
      (j/call :navigate (get screen-key-name-mapping screen-key) (j/lit {}))))
(reg-fx :effect/navigate navigate)

(reg-fx :effect/load-playback-object
        (fn [{:feed-item/keys [url playback-position]
             feed-item-id    :feed-item/id
             feed-id         :feed/id}]
          (tap> {:location :effect/load-playback-object :url url})
          (go
            ;; unload the old selected item
            (-> @playback-object
                (j/call :unloadAsync)
                <p!)
            ;; load the new item
            (-> @playback-object
                (j/call :loadAsync
                        (j/lit {:uri url})
                        (j/lit {:positionMillis playback-position}))
                <p!
                ;; set duration and position for new item
                ((fn [load-result]
                   (>evt [:event/update-feed-item
                          {:feed-item/id       feed-item-id
                           :feed/id            feed-id
                           :feed-item/duration (j/get load-result :durationMillis)
                           :feed-item/position (j/get load-result :positionMillis)}]))))
            ;; set playback status update fn
            (-> @playback-object
                (j/call :setOnPlaybackStatusUpdate
                        (fn [AVPlaybackStatus]
                          (j/let [^:js {:keys [isLoaded
                                               isPlaying
                                               didJustFinish
                                               positionMillis
                                               durationMillis]}
                                  AVPlaybackStatus

                                  status
                                  (if isLoaded
                                    (if isPlaying
                                      :status/playing
                                      (if didJustFinish
                                        :status/stopped
                                        :status/paused))
                                    ;; TODO justin 2021-04-05 should `isBuffering` be used here?
                                    ;; it seems like there could be an instance where nothing is loaded
                                    ;; and it just sits with a spinner otherwise
                                    :status/loading)]
                            (tap> {:location "playback status update"
                                   :status   AVPlaybackStatus})
                            (>evt [:event/update-feed-item
                                   {:feed-item/id       feed-item-id
                                    :feed/id            feed-id
                                    :feed-item/duration (j/get AVPlaybackStatus :durationMillis)
                                    :feed-item/position (j/get AVPlaybackStatus :positionMillis)}])
                            (>evt [:event/update-selected-item-status
                                   {:status status}]))))))))

(comment
  (go
    (-> @playback-object
        ;; (j/call :getStatusAsync)
        (j/call :pauseAsync)
        <p!
        tap>))
  )
