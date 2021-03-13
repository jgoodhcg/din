(ns backend.main
  (:require
   ["chrome-aws-lambda" :as chromium]
   [applied-science.js-interop :as j]
   [cljs.core.async :refer [go]]
   [cljs.core.async.interop :refer [<p!]]))

(defn log [& args] (apply (.-log js/console) args))

(defn handler [event context callback]
  (go
    (log "enter the go block")
    (let [{:keys [body]}                 (-> event (js->clj :keywordize-keys true))
          {:keys [graph email password]} (-> body js/JSON.parse (js->clj :keywordize-keys true))
          maybe-path                     (<p! (-> chromium (j/get :executablePath)))
          puppeteer                      (-> chromium (j/get :puppeteer))
          browser                        (<p! (-> puppeteer
                                                  (j/call
                                                    :launch
                                                    (clj->js
                                                      (merge
                                                        {:args              (-> chromium (j/get :args))
                                                         :defaultViewPort   (-> chromium (j/get :defaultViewPort))
                                                         :headless          (if (some? maybe-path)
                                                                              (-> chromium (j/get :headless))
                                                                              true)
                                                         :ignoreHTTPSErrors true}
                                                        (when maybe-path {:executablePath maybe-path}))))))
          page                           (<p! (-> browser (j/call :newPage)))]

      (try
        (log "made it to the try")
        (-> page (j/call :setDefaultTimeout 240000))
        (log "set timeout")
        (<p! (-> page (j/call :goto (str "https://roamresearch.com/#/app/" graph))))
        (log (str "got to roam with graph name: " graph))
        (<p! (-> page (j/call :waitForNavigation)))
        (log "waited for nav")
        (<p! (-> page (j/call :waitForSelector "input[name=email]")))
        (log "waited for email input")
        (<p! (-> page (j/call :type "input[name=email]" email)))
        (log (str "typed email"))
        (<p! (-> page (j/call :type "input[name=password]" password)))
        (log "typed password")
        (<p! (-> page (j/call :click ".bp3-button")))
        (log "pressed login button")
        (<p! (-> page (j/call :waitForSelector ".bp3-icon-more")))
        (log "waited for graph load")
        (let [titles (<p! (-> page (j/call :evaluate
                                           (fn [q]
                                             (js/Promise.resolve
                                               (.q (.-roamAlphaAPI js/window) q)))
                                           "[:find ?n :where [?e :node/title ?n]]")))]

          (log (str "got " (count (js->clj titles)) " titles"))

          (callback
            nil
            (clj->js
              {:statusCode 200
               :body
               (js/JSON.stringify
                 (clj->js {:titles titles}))})))

        (catch js/Error err
          (log "caught error")
          (log (ex-cause err))
          (callback nil (clj->js {:statusCode 500 :body err}))))

      (.close browser))))

(comment
  (handler {:body (->  {:email    "jgoodhcg@gmail.com"
                        :graph    "jgood-brain"
                        :password "2rsUH4$GBZ6g2FDBcd3dx"}
                       clj->js
                       js/JSON.stringify)}
           nil
           #(tap> %2))
  )
