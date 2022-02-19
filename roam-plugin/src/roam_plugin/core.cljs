(ns roam-plugin.core
  (:require
            ["@supabase/supabase-js" :as sp]

            [applied-science.js-interop :as j]
            [cljs.core.async :refer [go <!]]
            [cljs.core.async.interop :refer [<p!]]

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
              (j/call :signIn (j/lit {:email email :password password}))
              <p!
              (doto println))
          )
        )
      )))

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
   (let [supabase         (-> sp (j/call :createClient supabase-url supabase-anon-key))
         maybe-access-key (-> js/localStorage (j/call :getItem "din-roam-supabase-user-token"))]
     (if (some? maybe-access-key)
       ;; get last sync
       ;; sync pages modified since then
       ;; set up sync interval
       (println (str "all logged in: " maybe-access-key))
       ;; prompt to login
       (login supabase)))))
