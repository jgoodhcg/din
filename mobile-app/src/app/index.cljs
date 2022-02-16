(ns app.index
  (:require
   ["@react-navigation/native" :as nav]
   ["@react-navigation/native-stack" :as rn-stack]
   ["expo" :as ex]
   ["react" :as react]
   ["react-native" :as rn]
   ["react-native-gesture-handler" :as g]
   ["react-native-paper" :as paper]
   ["tailwind-rn" :default tailwind-rn]
   ["../aws-exports" :default aws-config]
   ["aws-amplify" :default Amplify :refer [Auth]]
   ["aws-amplify-react-native" :refer [withAuthenticator]]

   [applied-science.js-interop :as j]
   [cljs.core.async :refer [go <!]]
   [cljs.core.async.interop :refer [<p!]]
   [cljs-http.client :as http]
   [reagent.core :as r]
   [re-frame.core]
   [shadow.expo :as expo]

   [app.fx :refer [!navigation-ref]]
   [app.handlers]
   [app.subscriptions]
   [app.helpers :refer [<sub >evt >evt-sync screen-key-name-mapping screen-key->name]]
   [app.screen.feeds :refer [root] :rename {root feeds-screen}]
   [app.screen.feed :refer [root] :rename {root feed-screen}]
   [app.screen.feed-item :refer [root] :rename {root feed-item-screen}]
   [app.screen.subscription :refer [root] :rename {root subscription-screen}]
   [app.screen.settings :refer [root] :rename {root settings-screen}]
   [app.screen.account :refer [root] :rename {root account-screen}]
   [app.screen.login :refer [root] :rename {root login-screen}]
   [app.screen.signup :refer [root] :rename {root signup-screen}]
   ))

(def api-endpoint "https://yabrbam9si.execute-api.us-east-2.amazonaws.com/default/din-page-titles")

(def stack (rn-stack/createNativeStackNavigator))

(defn navigator [] (-> stack (j/get :Navigator)))

(defn screen [props] [:> (-> stack (j/get :Screen)) props])

(defn wrap-screen
  [the-screen]
  (g/gestureHandlerRootHOC
   (paper/withTheme the-screen)))

(defn settings-button [] ;; name this "global?"
  (r/as-element
   ;; TODO 2022-02-16 Justin Add logged in identifier?
   [:> paper/IconButton
    {:icon     "cog-outline"
     :on-press #(>evt [:event/navigate :screen/settings])}]))

(defn auth-button []
  (r/as-element
   (let [user nil]
     [:> paper/IconButton
      {:icon (if (some? user) "account-check" "account-cancel-outline")
       :on-press #(>evt [:event/navigate :screen/account])}])))

(defn root []
  (let [theme           (<sub [:sub/theme])
        !route-name-ref (clojure.core/atom {})
        last-screen     (<sub [:sub/last-screen])]

    [:> paper/Provider
     {:theme (case theme
               :light paper/DefaultTheme
               :dark  paper/DarkTheme
               paper/DarkTheme)}

     [:> nav/NavigationContainer
      {:theme           (-> nav (j/get :DarkTheme))
       :ref             (fn [el]
                          (reset! !navigation-ref el))
       :on-ready        (fn []
                          (swap! !route-name-ref merge {:current (-> @!navigation-ref
                                                                     (j/call :getCurrentRoute)
                                                                     (j/get :name))}))
       :on-state-change (fn []
                          (let [prev-route-name    (-> @!route-name-ref :current)
                                current-route-name (-> @!navigation-ref
                                                       (j/call :getCurrentRoute)
                                                       (j/get :name))]
                            (when (not= prev-route-name current-route-name)
                              ;; This is where you can do side effecty things like analytics
                              (>evt [:event/save-navigation current-route-name]))
                            (swap! !route-name-ref merge {:current current-route-name})))}

      [:> (navigator) {:header-mode        "none"
                       :initial-route-name (:screen/account screen-key-name-mapping)
                       ;; (screen-key->name last-screen) ;; use this for editing a screen quickly without re-navigating on hot reload
                       }
       (screen {:name      (:screen/feeds screen-key-name-mapping)
                :component (wrap-screen feeds-screen)
                :options   {:headerRight auth-button}})
       (screen {:name      (:screen/feed screen-key-name-mapping)
                :component (wrap-screen feed-screen)
                :options   {:headerRight auth-button}})
       (screen {:name      (:screen/feed-item screen-key-name-mapping)
                :component (wrap-screen feed-item-screen)
                :options   {:headerRight auth-button}})
       (screen {:name      (:screen/subscription screen-key-name-mapping)
                :component (wrap-screen subscription-screen)
                :options   {:headerRight auth-button}})
       (screen {:name      (:screen/settings screen-key-name-mapping)
                :component (wrap-screen settings-screen)
                :options   {:headerRight auth-button}})
       (screen {:name      (:screen/account screen-key-name-mapping)
                :component (wrap-screen account-screen)
                :options   {:headerRight auth-button}})
       (screen {:name      (:screen/login screen-key-name-mapping)
                :component (wrap-screen login-screen)
                :options   {:headerRight auth-button}})
       (screen {:name      (:screen/signup screen-key-name-mapping)
                :component (wrap-screen signup-screen)
                :options   {:headerRight auth-button}})]]]))

(defn start
  {:dev/after-load true}
  []
  (expo/render-root (r/as-element [root])
    ;; (r/as-element
    ;;   [(r/adapt-react-class
    ;;      (withAuthenticator
    ;;       (r/reactify-component root)
    ;;       (clj->js {:signUpConfig {:hiddenDefaults ["phone_number"]}})))])
    ))

(defn init []
  (println "hellooooooooooo ----------------------------------------------------")
  (j/call Amplify :configure aws-config)
  (>evt-sync [:event/initialize-db])
  (>evt [:event/trigger-load-db])
  (>evt [:event/set-auth-listener])
  (start))

(comment
  (g/gestureHandlerRootHOC (paper/withTheme feeds-screen)))
(comment
  ;; Get user info
  (go (-> Auth
          (j/call :currentUserInfo)
          <p!
          js->clj
          tap>))

  ;; Get jwt
  (go (-> Auth
          (j/call :currentSession)
          <p!
          (j/call :getIdToken)
          (j/get :jwtToken)
          tap>))

  ;; Call api
  (def jwt (atom nil))

  (go (-> Auth

          (j/call :currentSession)
          <p!
          (j/call :getIdToken)
          (j/get :jwtToken)
          (->> (reset! jwt))))

  (go (-> api-endpoint
          (http/post {:with-credentials? false
                      :headers           {"Authorization" (str "Bearer " @jwt)}
                      :json-params       {:email    "xxx"
                                          :graph    "xxx"
                                          :password "xxx"}})
          <!
          ;; :body
          ;; (#(-> js/JSON (j/call :parse %)))
          ;; (js->clj :keywordize-keys true)
          ;; :result
          tap>))

  (go (-> "https://rf8gjfxxbd.execute-api.us-east-2.amazonaws.com/default/din-eql"
          (http/post {:with-credentials? false
                      :headers           {"Authorization" (str "Bearer " @jwt)}
                      :json-params       {:transit-req "[\"~:stripe/publishable-key\"]"}})
          <!
          ;; :body
          ;; (#(-> js/JSON (j/call :parse %)))
          ;; (js->clj :keywordize-keys true)
          ;; :result
          tap>))
  )
