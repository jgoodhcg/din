(ns roam-plugin.core
  (:require ["@supabase/supabase-js" :as sp]

            [applied-science.js-interop :as j]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer [<p!]]
            [potpuri.core :as pot]

            [roam-plugin.secrets :refer [supabase-anon-key supabase-url]]))

(def login-form
  "<div class=\"container\" style=\"margin-top: 80px;\">
    <h3> Login to Din </h3>
    <label ><b>Username</b></label>
    <input type=\"text\" placeholder=\"Enter Username\" id=\"din-email\" style=\"color: black; background-color: white\">

    <label ><b>Password</b></label>
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

(defn sync [supabase]
  (go
    (let [user       (-> supabase
                         (j/get :auth)
                         (j/call :user))
          user-id    (-> user (j/get :id))
          s-res      (-> supabase
                         ;; TODO 2022-02-20 Justin add common key conversion stuff
                         (j/call :from "roam__sync")
                         (j/call :select "roam_sync__latest")
                         <p!)
          s-error    (-> s-res (j/get :error))
          last-sync  (or (-> s-res
                             (j/get :data)
                             (js->clj :keywordize-keys true)
                             first
                             ;; TODO 2022-02-20 Justin add common key conversion stuff
                             :roam_sync__latest)
                         0)
          titles     (-> js/roamAlphaAPI
                         (j/call :q titles-query)
                         (js->clj :keywordize-keys true)
                         ;; a little overlap since last sync in case the user is editing while this runs or something
                         (->> (filter #(> (nth % 3) (-> last-sync (- 1000))))))
          title-rows (->> titles
                          (mapv (fn [[_ title uid edit]]
                                  ;; TODO 2022-02-20 Justin add common key conversion stuff
                                  (j/lit {:user__id    user-id
                                          :block__uid  uid
                                          :node__title title
                                          :edit__time  edit}))))
          u-res      (-> supabase
                         ;; TODO 2022-02-20 Justin add common key conversion stuff
                         (j/call :from "roam__pages")
                         (j/call :upsert (clj->js title-rows))
                         <p!)
          u-error    (-> u-res (j/get :error))
          now        (-> js/Date (j/call :now))]

      (when (and (nil? s-error) (nil? u-error))
        (-> supabase
            ;; TODO 2022-02-20 Justin add common key conversion stuff
            (j/call :from "roam__sync")
            (j/call :upsert (j/lit {:user__id          user-id
                                    :roam_sync__latest now}))
            <p!)))))

(defn init-sync [supabase]
  (sync supabase)
  (js/setInterval #(sync supabase) 60000))

(defn login-submit-gen [supabase]
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
                  (init-sync supabase))
              (js/alert "Din Login failed"))))))))

(defn login [supabase]
  (let [app        (-> js/document (j/call :getElementById "app"))
        login-root (-> js/document (j/call :createElement "div"))]
    (-> login-root (j/assoc! :id (str "din-login-root")))
    (-> login-root (j/assoc! :style overlay-style))
    (-> login-root (j/assoc! :innerHTML login-form))
    (-> app (j/get :parentElement) (j/call :appendChild login-root))
    (-> js/document
        (j/call :getElementById "din-login-submit")
        (j/call :addEventListener "click" (login-submit-gen supabase)))
    (-> js/document
        (j/call :getElementById "din-login-cancel")
        (j/call :addEventListener "click" remove-login-root))))

;; self executing anonymous function
;; this should "hide" my anon key and supabase client from other scripts in the roam graph
;; anon key would probably be easy to spot from source file though ...
((fn []
   (let [supabase (-> sp (j/call :createClient supabase-url supabase-anon-key))
         user     (-> supabase (j/get :auth) (j/call :user))]
     (if (some? user)
       ;; get last sync
       ;; sync pages modified since then
       ;; set up sync interval
       (init-sync supabase)
       ;; prompt to login
       (login supabase)))))
