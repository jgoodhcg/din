(ns roam-plugin.core
  (:require
            ["@supabase/supabase-js" :as sp]

            [applied-science.js-interop :as j]
            [cljs.core.async :refer [go <!]]
            [cljs.core.async.interop :refer [<p!]]
            [potpuri.core :as pot]

            [roam-plugin.secrets :refer [supabase-anon-key
                                         supabase-url]]
            ))

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

(defn init-sync [supabase]
  (go
    (let [user       (-> supabase
                         (j/get :auth)
                         (j/call :user))
          user-id    (-> user (j/get :id))
          res        (-> supabase
                         (j/call :from "roam__sync")
                         (j/call :select "roam_sync__latest")
                         <p!)
          last-sync  (or (-> res
                             (j/get :data)
                             (js->clj :keywordize-keys true)
                             first)
                         0)
          titles     (-> js/roamAlphaAPI
                         (j/call :q titles-query)
                         (js->clj :keywordize-keys true)
                         (->> (filter #(> (nth % 3) last-sync))))
          title-rows (->> titles
                          (mapv (fn [[_ title uid edit]]
                                  (j/lit {:user__id    user-id
                                          :block__uid  uid
                                          :node__title title
                                          :edit__time  edit}))))]

      (println (pot/map-of last-sync (count title-rows)))
      (println (-> supabase
                   (j/call :from "roam__pages")
                   (j/call :upsert (clj->js title-rows))
                   <p!))
      (println "pages should be there")


      )))

(defn login-submit-gen [supabase]
  (fn []
    (let [email    (-> js/document (j/call :getElementById "din-email") (j/get :value))
          password (-> js/document (j/call :getElementById "din-password") (j/get :value))]
      (when (empty? email) (js/alert "Please enter din email to login"))
      (when (empty? password) (js/alert "Please enter din password to login"))
      (when (and (not-empty email) (not-empty password))
        (go
          (-> supabase
              (j/get :auth)
              (j/call :signIn (j/lit {:email    email
                                      :password password}))
              <p!)
          (let [user (-> supabase (j/get :auth) (j/call :user))]
            (if (some? user)
              (do (remove-login-root)
                  (init-sync supabase))
              (js/alert "Login failed"))))))))

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
        (j/call :addEventListener "click" remove-login-root))
   )
  )

;; self executing anonymous function
;; this should "hide" my anon key and supabase client from other scripts in the roam graph
((fn []
   (let [supabase (-> sp (j/call :createClient supabase-url supabase-anon-key))
         user     (-> supabase (j/get :auth) (j/call :user))]
     (println user)
     (if (some? user)
       ;; get last sync
       ;; sync pages modified since then
       ;; set up sync interval
       (init-sync supabase)
       ;; prompt to login
       (login supabase)))))
