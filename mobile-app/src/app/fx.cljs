(ns app.fx
  (:require
   ["aws-amplify" :default Amplify :refer [Auth Hub]]
   ["react-native-rss-parser" :as rss]
   ["react-native" :as rn]
   ["expo-av" :as av]
   ["expo-file-system" :as fs]
   ["expo-secure-store" :as ss]
   ["expo-constants" :as expo-constants]
   ["react-native-controlled-mentions" :as cm]
   ["react-native-url-polyfill/auto"]
   ["@supabase/supabase-js" :as spb]

   [re-frame.core :refer [reg-fx]]
   [applied-science.js-interop :as j]
   [cljs.core.async :refer [go <!]]
   [cljs.core.async.interop :refer [<p!]]
   [cljs-http.client :as http]
   [clojure.edn :as edn]
   [clojure.set :refer [rename-keys]]
   [cognitect.transit :as transit]
   [potpuri.core :as p]
   [com.rpl.specter :as sp :refer [transform select]]
   [tick.core :as t]

   [app.helpers :refer [>evt
                        screen-key-name-mapping
                        millis->str]]
   [pg :refer [pg->k k->pg]]
   [app.secrets :refer [supabase-url supabase-anon-key]]
   [app.screen.feed-item :refer [note-input-ref]]))

(def supabase
  (-> spb
      (j/call :createClient
              supabase-url
              supabase-anon-key
              (j/lit {:localStorage {:getItem    (j/get ss :getItemAsync)
                                     :setItem    (j/get ss :setItemAsync)
                                     :removeItem (j/get ss :deleteItemAsync)}}))))

(comment
  (-> supabase
      (j/get :auth)
      (j/call :signUp (j/lit {:email    "jgoodhcg+test1@gmail.com"
                              :password "mysecurepassword"})))
  )

(def writer (transit/writer :json))

(def reader (transit/reader :json))

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
        (fn [app-db]
          (-> fs (j/call :writeAsStringAsync
                         app-db-file
                         (-> app-db
                             (dissoc :supabase)
                             (->> (transit/write writer)))))))

(comment
  (-> ss (j/call :setItemAsync "test-key" "test-value"))
  (go (-> ss (j/call :getItemAsync "test-key") <p! tap>))
  (go (-> ss (j/call :getItemAsync "roam-credentials") <p! (->> (transit/read reader)) tap>))
  )

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
                               (->> (transit/read reader))
                               (#(>evt [:event/load-app-db {:app-db  {} ;; TODO this should be %
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
                            (>evt [:event/update-feed-item
                                   {:feed-item/id feed-item-id
                                    :feed/id      feed-id
                                    :feed-item    {:feed-item/duration (or (j/get AVPlaybackStatus :durationMillis) 0)
                                                        :feed-item/position (or (j/get AVPlaybackStatus :positionMillis) 0)}}])
                            (>evt [:event/update-selected-item-status
                                   {:status status}])
                            )))))))

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

(comment
  (-> @re-frame.db/app-db :stripe tap>)
  (-> Auth (j/call :signOut))
  )

(defn <get-all-items
  [table-key]
  (go
    (let [item-count (-> supabase
                         (j/call :from (k->pg table-key))
                         (j/call :select "*" (j/lit {:count "exact"}))
                         (j/call :range 0 9)
                         <p!
                         (j/get :count))]
      (loop [start 0
             end   999
             items []]
        (if (-> start (< end))
          (let [new-items (-> supabase
                              (j/call :from (k->pg table-key))
                              (j/call :select "*" (j/lit {:count "exact"}))
                              (j/call :range start end)
                              <p!
                              (j/get :data)
                              js->clj
                              (->> (transform [sp/ALL sp/MAP-KEYS] pg->k)))
                new-start (-> end (+ 1))
                new-end (-> end (+ 1000) (min item-count))
                all-items (vec (concat items new-items))]
            (recur new-start new-end all-items))
          items)))))

(comment
  (go (-> :roam/pages <get-all-items <! count tap>))
  (go (->> :roam/pages <get-all-items <! (select [sp/ALL :node/title]) tap>))
  )

(reg-fx :effect/init-for-logged-in-user
        (fn []
          (tap> {:location :effect/init-for-logged-in-user})
          (go
            (let [user (-> supabase
                           (j/get :auth)
                           (j/call :user)
                           (js->clj :keywordize-keys true))]
              (tap> {:location :effect/init-for-logged-in-user
                     :user     user})
              (when (some? user)
                (go
                  (>evt [:event/reset-roam-pages
                         (->> :roam.pages
                             <get-all-items
                             <!
                             ;; TODO 2022-07-06 Justin - filter for graph id?
                             (select [sp/ALL :roam.pages/node-title]))])
                  (>evt [:event/set-supabase-user user])
                  ))))))

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

(reg-fx :effect/supabase-sign-in
        (fn [{email    :supabase/email
              password :supabase/password}]
          (go
            (let [location :effect/supabase-sign-in
                  res      (-> supabase
                            (j/get :auth)
                            (j/call :signIn (j/lit {:email    email
                                                    :password password}))
                            <p!)
                  error    (-> res (j/get :error) (j/get :message))
                  user     (-> res (j/get :user) (js->clj :keywordize-keys true))]
              (tap> (p/map-of error user location))
              (if (some? error)
                (>evt [:event/set-sign-in-error error])
                (do
                  (>evt [:event/set-sign-in-error nil])
                  (>evt [:event/init-for-logged-in-user])
                  ;; TODO 2022-02-22 Justin: Pop off the navigation stack
                  ;; TODO 2022-02-24 Justin: init for logged in user
                  (>evt [:event/navigate :screen/account])
                  ))))))

(reg-fx :effect/supabase-sign-up
        (fn [{email    :supabase/email
              password :supabase/password}]
          (go
            (let [res (-> supabase
                          (j/get :auth)
                          (j/call :signUp (j/lit {:email    email
                                                  :password password}))
                          <p!)
                  error    (-> res (j/get :error) (j/get :message))
                  user     (-> res (j/get :user) (js->clj :keywordize-keys true))]
              (if (some? error)
                (>evt [:event/set-sign-up-error error])
                (do
                  (>evt [:event/set-sign-up-error nil])
                  (>evt [:event/set-supabase-user user])
                  ;; TODO 2022-02-22 Justin: Pop off the navigation stack
                  ;; TODO 2022-02-24 Justin: init for logged in user ?
                  (>evt [:event/navigate :screen/feeds])
                  ))))))

(reg-fx :effect/supabase-sign-out
        (fn []
          (go
            (let [location :effect/supabase-sign-out
                  res      (-> supabase
                            (j/get :auth)
                            (j/call :signOut)
                            <p!)
                  error    (-> res (j/get :error) (j/get :message))]
              (tap> (p/map-of res location))
              (-> ss (j/call :deleteItemAsync "supabase.auth.token"))))))

(comment
  (go (-> supabase
          (j/get :auth)
          (j/call :signIn {:email    ""
                           :password ""})
          <p! println))
  (go (-> supabase (j/get :auth) (j/call :signOut) <p! println))
  (-> supabase (j/get :auth) (j/call :user) println)
  (-> supabase (j/get :auth) (j/call :session) (j/get :access_token) println)
  (go (-> supabase
          (j/get :auth)
          (j/get :api)
          (j/call :getUser "")
          <p!
          println))
  (>evt [:event/sign-out])

  ;; password recovery
  ;; use supabase console to send user recovery email
  ;; take accessToken from email link redirect and use here in :setAuth
  (go (-> supabase
          (j/get :auth)
          (j/call :setAuth "")
          <p!
          println))

  ;; once authenticated with recovery access token reset password with this
  (go (-> supabase
          (j/get :auth)
          (j/call :update (j/lit {:password ""}))
          <p!
          println))
  )

(reg-fx :effect/set-keyboard-listener
        (fn []
          (-> rn/Keyboard (j/call :addListener "keyboardDidShow"
                                  (fn [_] (>evt [:event/set-keyboard-showing true]))))
          (-> rn/Keyboard (j/call :addListener "keyboardDidHide"
                                  (fn [_] (>evt [:event/set-keyboard-showing false]))))))

(comment
  (def test-listener
    (-> rn/Keyboard (j/call :addListener "keyboardDidShow"
                            (fn [x] (tap> {:x x :showing true})))))
  )

(reg-fx :effect/add-page-link-to-text-input
        (fn [{start :note-selection/start
              end   :note-selection/end
              text  :feed-item-note/text}]
          (tap> (p/map-of :effect/add-page-link-to-text-input start end text))
          (-> @note-input-ref
              (j/call :setNativeProps (j/lit {:selection    (p/map-of start end)
                                              :defaultValue text})))))

(reg-fx :effect/set-persist-handler
        (fn [_]
          (-> rn/AppState
              (j/call :addEventListener
                      "change"
                      (fn [next-app-state]
                        ;; Persisting the app-db to the file system happens on putting the app in the background
                        ;; Doing it on "active" results in some lag after the ui loads

                        ;; This doesn't work on closing the app (only backgrounding it) -- on android
                        ;; I guess ios works on closing ... uncomfirmed
                        (when (not= next-app-state "active")
                          (>evt [:event/persist-app-db])))))))
