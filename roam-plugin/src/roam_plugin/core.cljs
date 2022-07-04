(ns roam-plugin.core
  (:require ["@supabase/supabase-js" :as sp]

            [applied-science.js-interop :as j]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer [<p!]]
            [potpuri.core :as pot]
            [clojure.string :refer [split]]
            [com.rpl.specter :as specter :refer [transform select]]

            [roam-plugin.secrets :refer [supabase-anon-key supabase-url]]
            [pg :refer [pg->k k->pg]]))

(def login-form
  "<div class=\"container\" style=\"margin-top: 80px;background-color: grey;padding: 10px;border-radius: 2px;\">
    <h3> Login to Din </h3>
    <label style=\"color: white\"><b>Username</b></label>
    <input type=\"text\" placeholder=\"Enter Username\" id=\"din-email\" style=\"color: black; background-color: white\">

    <label style=\"color: white\"><b>Password</b></label>
    <input type=\"password\" placeholder=\"Enter Password\" id=\"din-password\" style=\"color: black; background-color: white\">

    <button id=\"din-login-submit\" style=\"color: black; background-color: rgb(239, 239, 239)\" >Login</button>
    <button id=\"din-login-cancel\" style=\"color: black; background-color: rgb(239, 239, 239)\" >cancel</button>
</div>")

(def overlay-style
  "position: absolute;
   top: 0;
   left: 0;
   width: 100%;
   height: 100%;
   z-index: 10;
   background-color: rgba(0,0,0,0.6); /*dim the background*/")

(defn remove-login-root []
  (-> js/document
      (j/call :getElementById "din-login-root")
      (j/call :remove)))

(defn interval-sync [])

(def titles-query
  "[:find ?p ?title ?uid ?edit
    :where [?p :node/title ?title]
           [?p :block/uid ?uid]
           [?p :edit/time ?edit]]")

(defn sync [supabase graph-id]
  (go
    (let [user       (-> supabase
                         (j/get :auth)
                         (j/call :user))
          user-id    (-> user (j/get :id))
          s-res      (-> supabase
                         (j/call :from (k->pg :roam/sync))
                         (j/call :select (k->pg :roam.sync/latest))
                         <p!)
          s-error    (-> s-res (j/get :error))
          last-sync  (or (-> s-res
                             (j/get :data)
                             js->clj
                             (->> (transform [sp/ALL sp/MAP-KEYS] pg->k))
                             (->> (filter #(= graph-id (% :roam.sync/graph-id))))
                             first
                             :roam.sync/latest)
                         0)
          titles     (-> js/roamAlphaAPI
                         (j/call :q titles-query)
                         (js->clj :keywordize-keys true)
                         ;; a little overlap since last sync in case the user is editing while this runs or something
                         (->> (filter #(> (nth % 3) (-> last-sync (- 1000))))))
          title-rows (->> titles
                          (mapv (fn [[_ title uid edit]]
                                  (j/lit {(k->pg :user/id)    user-id
                                          (k->pg :block/uid)  uid
                                          (k->pg :node/title) title
                                          (k->pg :edit/time)  edit}))))
          u-res      (-> supabase
                         (j/call :from (k->pg :roam/pages))
                         (j/call :upsert (clj->js title-rows))
                         <p!)
          u-error    (-> u-res (j/get :error))
          now        (-> js/Date (j/call :now))]

      (when (and (nil? s-error) (nil? u-error))
        (-> supabase
            (j/call :from (k->pg :roam/sync))
            (j/call :upsert (j/lit {(k->pg :user/id)          user-id
                                    (k->pg :roam.sync/latest) now}))
            <p!)))))

(defn init-sync [supabase graph-id]
  (sync supabase graph-id)
  (js/setInterval #(sync supabase graph-id) 60000))

(defn login-submit-gen [supabase graph-id]
  (fn []
    (let [email    (-> js/document (j/call :getElementById "din-email") (j/get :value))
          password (-> js/document (j/call :getElementById "din-password") (j/get :value))]
      (when (empty? email) (js/alert "Please enter din email to login"))
      (when (empty? password) (js/alert "Please enter din password to login"))
      (when (and (not-empty email) (not-empty password))
        (go
          (let [res (-> supabase
                        (j/get :auth)
                        (j/call :signIn (j/lit {:email    email
                                                :password password}))
                        <p!)
                error (-> res (j/get :error))
                user (-> supabase (j/get :auth) (j/call :user))]

            ;; TODO 2022-02-21 Justin use res instead of calling auth to get user
            (when (some? error) (println (pot/map-of error)))

            (if (some? user)
              (do (remove-login-root)
                  #_(init-sync supabase graph-id))
              (js/alert "Din Login failed"))))))))

(defn get-graph-id [url]
  (-> url (split "#") second (split "app/") second (split "/") first))

(defn login [supabase graph-id]
  (let [app        (-> js/document (j/call :getElementById "app"))
        login-root (-> js/document (j/call :createElement "div"))]
    (-> login-root (j/assoc! :id (str "din-login-root")))
    (-> login-root (j/assoc! :style overlay-style))
    (-> login-root (j/assoc! :innerHTML login-form))
    (-> app (j/get :parentElement) (j/call :appendChild login-root))
    (-> js/document
        (j/call :getElementById "din-login-submit")
        (j/call :addEventListener "click" (login-submit-gen supabase graph-id)))
    (-> js/document
        (j/call :getElementById "din-login-cancel")
        (j/call :addEventListener "click" remove-login-root))))

;; self executing anonymous function
;; this should "hide" my anon key and supabase client from other scripts in the roam graph
;; anon key would probably be easy to spot from source file though ...
((fn []
   (let [supabase (-> sp (j/call :createClient supabase-url supabase-anon-key))
         user     (-> supabase (j/get :auth) (j/call :user))
         graph-id (-> js/window (j/get :location) (j/get :href)
                      get-graph-id)]
     (println (pot/map-of graph-id user))
     (if (some? user)
       ;; get last sync
       ;; sync pages modified since then
       ;; set up sync interval
       (init-sync supabase graph-id)
       ;; prompt to login
       (login supabase graph-id)))))
