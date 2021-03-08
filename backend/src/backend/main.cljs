(ns backend.main
  (:require
   ["chrome-aws-lambda" :as chromium]
   [applied-science.js-interop :as j]
   [cljs.core.async :refer [go]]
   [cljs.core.async.interop :refer [<p!]]))

(defn handler [event context callback]
  (go
    (let [{:keys [graph
                  email
                  password]} event

          maybe-path (<p! (-> chromium (j/get :executablePath)))
          puppeteer  (-> chromium (j/get :puppeteer))
          browser    (<p! (-> puppeteer
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
          page       (<p! (-> browser (j/call :newPage)))]
      (try
        (-> page (j/call :setDefaultTimeout 60000))
        (<p! (-> page (j/call :goto (str "https://roamresearch.com/#/app/" graph))))
        (tap> "made it to the graph")
        (<p! (-> page (j/call :waitForNavigation)))
        (tap> "waited for navigation")
        (<p! (-> page (j/call :waitForSelector "input[name=email]")))
        (tap> "waited for selector")
        (<p! (-> page (j/call :type "input[name=email]" email)))
        (tap> "typed in email")
        (<p! (-> page (j/call :type "input[name=password]" password)))
        (tap> "typed in password")
        (<p! (-> page (j/call :click ".bp3-button")))
        (tap> "clicked login")
        (<p! (-> page (j/call :waitForSelector ".bp3-icon-more")))
        (tap> "waited for load up")
        (let [titles (<p! (-> page (j/call :evaluate
                                           (fn [q]
                                             (js/Promise.resolve
                                               (.q (.-roamAlphaAPI js/window) q)))
                                           "[:find ?n :where [?e :node/title ?n]]")))]

          (tap> "ran query")
          (callback
            nil
            (clj->js
              {:statusCode 200
               :body
               (js/JSON.stringify
                 (clj->js {:event   event
                           :context context
                           :result  titles}))})))
        (catch js/Error err
          (tap> err)
          (js/console.log (ex-cause err))))
      (.close browser))))

(comment

  ;; query [:find ?n :where [?e :node/title ?n]]

  (handler {:email    "xxx"
            :graph    "xxx"
            :password "xxx"}
           nil
           #(tap> %2))

  )
