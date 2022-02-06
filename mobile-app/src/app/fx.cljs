(ns app.fx
  (:require
   ["aws-amplify" :default Amplify :refer [Auth Hub]]
   ["react-native-rss-parser" :as rss]
   ["react-native" :as rn]
   ["expo-av" :as av]
   ["expo-file-system" :as fs]
   ["expo-constants" :as expo-constants]
   ["react-native-controlled-mentions" :as cm]

   [re-frame.core :refer [reg-fx]]
   [applied-science.js-interop :as j]
   [cljs.core.async :refer [go <!]]
   [cljs.core.async.interop :refer [<p!]]
   [cljs-http.client :as http]
   [clojure.edn :as edn]
   [cognitect.transit :as transit]
   [tick.alpha.api :as t]

   [app.helpers :refer [>evt >evt-sync screen-key-name-mapping millis->str]]
   [potpuri.core :as p]))

(def dd (-> fs (j/get :documentDirectory)))

(def app-db-file (str dd "app-db.edn"))

(def version (-> expo-constants
                 (j/get :default)
                 (j/get :manifest)
                 (j/get :version)))

;; defonce so that hot reloads don't blow away the object while on the playback screen
(defonce playback-object (atom (av/Audio.Sound.)))

;; set up background modes
(go (<p! (j/call av/Audio :setAudioModeAsync
                 {:interruptionModeAndroid (j/get av/Audio :INTERRUPTION_MODE_ANDROID_DUCK_OTHERS)
                  :interruptionModeIOS     (j/get av/Audio :INTERRUPTION_MODE_IOS_DUCK_OTHERS)
                  :playsInSilentModeIOS    true
                  :staysActiveInBackground true})))

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
                         (>evt [:event/refresh-feeds])
                         (>evt [:event/init-for-logged-in-user]))
                       ;; file exists load db
                       (go
                         (try
                           (println "load fx: file exists -------------------------------")
                           (-> fs (j/call :readAsStringAsync app-db-file)
                               <p!
                               edn/read-string
                               (#(>evt [:event/load-app-db {:app-db  {}
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
        (fn [{url          :feed-item/url
             position     :feed-item/position
             :or          {position 0}
             feed-item-id :feed-item/id
             feed-id      :feed/id}]
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
                        (j/lit {:positionMillis position}))
                <p!
                ;; set duration and position for new item
                ((fn [load-result]
                   (>evt [:event/update-feed-item
                          {:feed-item/id feed-item-id
                           :feed/id      feed-id
                           :feed-item    {:feed-item/duration (j/get load-result :durationMillis)
                                          :feed-item/position (j/get load-result :positionMillis)}}])
                   (>evt [:event/update-selected-item-status
                          {:status :status/paused}]))))
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
                            (>evt-sync [:event/update-feed-item
                                        {:feed-item/id feed-item-id
                                         :feed/id      feed-id
                                         :feed-item    {:feed-item/duration (or (j/get AVPlaybackStatus :durationMillis) 0)
                                                        :feed-item/position (or (j/get AVPlaybackStatus :positionMillis) 0)}}])
                            (>evt-sync [:event/update-selected-item-status
                                        {:status status}]))))))))

(reg-fx :effect/play-selected-item
        #(go
           (-> @playback-object
               (j/call :playAsync)
               <p!)))

(reg-fx :effect/pause-selected-item
        #(go
           (-> @playback-object
               (j/call :pauseAsync)
               <p!)))

(reg-fx :effect/set-position-selected-item
        (fn [new-pos-millis]
          (go
            (-> @playback-object
                (j/call :setPositionAsync new-pos-millis)
                <p!))))

(reg-fx :effect/share
        (fn [{feed-title :feed/title
              item-title :feed-item/title
              position   :feed-item-note/position
              note-text  :feed-item-note/text}]
          (let [text         (-> note-text
                                 (cm/replaceMentionValues
                                   (fn [mt] (str "[[" (j/get mt :name) "]]"))))
                position-str (-> position millis->str)]
            (-> rn/Share
                (j/call :share
                        (j/lit {:message (str  feed-title " \n "
                                               item-title " \n "
                                               position-str " \n "
                                               text " \n "
                                               )}))))))

(reg-fx :effect/set-playback-rate
        #(go
           (tap> {:location :effect/set-playback-rate
                  :rate     %})
           (-> @playback-object
               (j/call :setStatusAsync (j/lit {:rate % :shouldCorrectPitch true}))
               <p!)))

(defn <load-stripe-data!
  []
  (go
    (tap> {:location "<load-stripe-data!"
           :msg      "loading stripe data"})
    (let [w   (transit/writer :json)
          r   (transit/reader :json)
          jwt (-> Auth
                  (j/call :currentSession)
                  <p!
                  (j/call :getIdToken)
                  (j/get :jwtToken))
          req [:user/email
               :stripe/free-pass
               {:stripe/active-subscription
                [:stripe.subscription/created
                 :stripe.subscription/current-period-end
                 :stripe.subscription/current-period-start
                 :stripe.subscription/cancel-at-period-end
                 :stripe.price/id]}
               {:stripe/prices
                [:stripe.price/id
                 :stripe.price/unit-amount
                 :stripe.product/name
                 :stripe.product/description
                 :stripe.product/images]}
               ]
          res (-> "https://rf8gjfxxbd.execute-api.us-east-2.amazonaws.com/default/din-eql"
                  (http/post {:with-credentials? false
                              :headers           {"Authorization" (str "Bearer " jwt)}
                              :json-params       {:transit-req (->> req (transit/write w))}})
                  <!
                  :body
                  (->> (transit/read r)))]
      (>evt [:event/set-stripe-data res]))))

(reg-fx :effect/set-auth-listener
        (fn []
          (println "auth listener fx ----------------------------------------------------")
          (tap> {:location "auth listener fx"})
          (-> Hub (j/call :listen "auth" (fn [msg]
                                           (let [event (-> msg (j/get-in [:payload :event]))]
                                             (when (= "signIn" event)
                                               (tap> {:location :effect/set-auth-listener
                                                      :msg      "Signed in, getting stripe data"})
                                               (go (<! (<load-stripe-data!))))
                                             (when (= "signOut" event)
                                               (tap> {:location :effect/set-auth-listener
                                                      :msg      "Signed out, removing stripe data"})
                                               (>evt [:event/set-stripe-data nil]))))))))

(comment
  (-> @re-frame.db/app-db :stripe tap>)
  (-> Auth (j/call :signOut))
  )

(reg-fx :effect/init-for-logged-in-user
        (fn []
          (println "auth listener fx ----------------------------------------------------")
          (tap> {:location :effect/init-for-logged-in-user})
          (go
            (let [is-logged-in (-> Auth
                                   (j/call :currentUserInfo)
                                   <p!
                                   js->clj
                                   some?)]

              (when is-logged-in
                (tap> {:location :effect/init-for-logged-in-user
                       :msg      "Is logged in, fetching stripe data"})
                (go (<! (<load-stripe-data!))))))))

(comment
  (go
    (-> @playback-object
        ;; (j/call :getStatusAsync)
        (j/call :pauseAsync)
        ;; (j/call :playAsync)
        <p!
        tap>))

  (let [w   (transit/writer :json)
        r   (transit/reader :json)
        req [:stripe/publishable-key]]
  (->> req (transit/write w) tap>)
  ;; (->> "[]" (t/read r))
  )

(go
  (-> Auth
      (j/call :signOut)
      <p!
      js->clj
      tap>))

(go
  (-> Auth
      (j/call :currentUserInfo)
      <p!
      js->clj
      (#(hash-map :x %))
      tap>))
  )
