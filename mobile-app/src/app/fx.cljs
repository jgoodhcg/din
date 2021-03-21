(ns app.fx
  (:require
   ["react-native-rss-parser" :as rss]
   ["react-native" :as rn]
   ["expo-file-system" :as fs]
   ["expo-constants" :as expo-constants]

   [re-frame.core :refer [reg-fx]]
   [applied-science.js-interop :as j]
   [cljs.core.async :refer [go <!]]
   [cljs.core.async.interop :refer [<p!]]
   [cljs-http.client :as http]
   [clojure.edn :as edn]

   [app.helpers :refer [>evt screen-key-name-mapping]]))

(defn <get-feed [url]
  (go (-> url
          http/get
          <!
          :body
          (->> (j/call rss :parse))
          <p!)))

(defn dispatch-update-feed [id feed]
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
  (go
    (let [feed (<! (<get-feed url))]
      (dispatch-update-feed id feed))))
(reg-fx :refresh-feed <refresh-feed)

(reg-fx :refresh-feeds
        (fn [feeds]
          (doall (->> feeds (map <refresh-feed)))))

(def dd (-> fs (j/get :documentDirectory)))

(def app-db-file (str dd "app-db.edn"))

(reg-fx :persist
        (fn [app-db-str]
          (println "persist fx ----------------------------------------------------")
          (-> fs (j/call :writeAsStringAsync
                         app-db-file
                         app-db-str))))

(def version (-> expo-constants
                 (j/get :default)
                 (j/get :manifest)
                 (j/get :version)))

(reg-fx :load
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
                         (>evt [:set-version version])
                         (>evt [:refresh-feeds]))
                       ;; file exists load db
                       (go
                         (try
                           (println "load fx: file exists -------------------------------")
                           (-> fs (j/call :readAsStringAsync app-db-file)
                               <p!
                               edn/read-string
                               (#(>evt [:load-app-db {:app-db  %
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
(reg-fx :navigate navigate)
