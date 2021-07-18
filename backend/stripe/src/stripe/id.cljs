(ns stripe.id
  (:require
   ["stripe" :as stripe-construct]
   [applied-science.js-interop :as j]
   [cljs.core.async :refer [go]]
   [cljs.core.async.interop :refer [<p!]]))

(defn log [& args] (apply (.-log js/console) args))

(defn handler [event context callback]
  (go
    (log "enter the go block")
    (let [
          {:keys [body]}      (-> event (js->clj :keywordize-keys true))
          {:keys [sub email]} (-> body js/JSON.parse (js->clj :keywordize-keys true))]

      (try
        (log "made it to the try")

        (catch js/Error err
          (log "caught error")
          (log (ex-cause err))
          (callback nil (clj->js {:statusCode 500 :body err}))))

      )))

(comment
  (handler {:body (->  {:sub   "xxx"
                        :email "xxx"}
                       clj->js
                       js/JSON.stringify)}
           nil
           #(tap> %2)))

(comment
  (tap> "test")

  ;; TODO this needs to come from envvars
  (def stripe (stripe-construct "sk_test_4eC39HqLyjWDarjtT1zdp7dc"))

  (go
    (-> stripe (j/get :customers) (j/call :list (j/lit {:email "michael@codeaterium.com"}))
        <p!
        (js->clj :keywordize-keys true)
        :data
        ;; TODO This is where we search through the list of objects looking for one with (get-in [:metadata :sub]) that is the sub from params
        tap>
        ))

  )
