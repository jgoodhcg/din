(ns backend.main
  (:require
   ["chrome-aws-lambda" :as chromium]
   [applied-science.js-interop :as j]
   [cljs.core.async :refer [go]]
   [cljs.core.async.interop :refer [<p!]]))

(defn handler [event context callback]
  (go
    (let [maybe-path (<p! (-> chromium (j/get :executablePath)))
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
        (<p! (-> page (j/call :goto "https://jgood.io")))
        (callback
          nil
          (clj->js
            {:statusCode 200
             :body
             (js/JSON.stringify
               (clj->js {:event   event
                         :context context
                         :result  (<p! (-> page (j/call :title))) }))}))
        (catch js/Error err (js/console.log (ex-cause err))))
      (.close browser))))

(comment

  ;; query [:find ?n :where [?e :node/title ?n]]

  (handler nil nil #(tap> %2))

  )
