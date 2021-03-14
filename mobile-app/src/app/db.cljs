(ns app.db
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.data-spec :as ds]))

(def feed-data-spec
  (ds/spec {:name ::feed-ds
            :spec {:feed/id                 uuid?
                   :feed/url                string?
                   (ds/opt :feed/title)     string?
                   (ds/opt :feed/image-url) string?}}))

(s/def ::feed feed-data-spec)

(s/def ::feeds (s/and map? (s/every-kv uuid? ::feed)))

(def app-db-spec
  (ds/spec {:spec {:settings {:theme (s/spec #{:light :dark})}
                   :version  string?
                   :feeds    ::feeds
                   :modals   {:modal/feed-add    {:feed-add/visible boolean?}
                              :modal/feed-remove {:feed-remove/id (ds/maybe uuid?)}}}
            :name ::app-db}))

(def default-app-db
  {:settings {:theme :dark}
   :version  "version-not-set"
   :feeds    {
              ;; indie hackers
              #uuid "e391e5f9-2d87-43e9-b64e-2ae2df13b475"
              {:feed/url "https://feeds.transistor.fm/the-indie-hackers-podcast"
               :feed/id  #uuid "e391e5f9-2d87-43e9-b64e-2ae2df13b475" }

              ;; startup to last
              #uuid "dcadf904-465b-4700-b948-f342067a95dd"
              {:feed/url "https://feeds.transistor.fm/startup-to-last"
               :feed/id  #uuid "dcadf904-465b-4700-b948-f342067a95dd"}

              ;; indie bites
              #uuid "b349bcac-c4c0-41ed-b15a-5da40e81e30b"
              {:feed/url "https://feeds.transistor.fm/indie-bites"
               :feed/id  #uuid "b349bcac-c4c0-41ed-b15a-5da40e81e30b"}

              ;; newsletter crew
              #uuid "79c90f1c-cbf6-435c-9eba-4066bef19fd0"
              {:feed/url "https://newslettercrew.com/rss/"
               :feed/id  #uuid "79c90f1c-cbf6-435c-9eba-4066bef19fd0"}

              ;; run with it
              #uuid "4f9323dd-f309-49b1-99d9-2b88cc95e1ed"
              {:feed/url "https://feeds.transistor.fm/run-with-it"
               :feed/id  #uuid "4f9323dd-f309-49b1-99d9-2b88cc95e1ed"}

              ;; software social
              #uuid "cca6a4b3-23aa-4055-a72f-0286108492ea"
              {:feed/url "https://feeds.transistor.fm/software-social"
               :feed/id  #uuid "cca6a4b3-23aa-4055-a72f-0286108492ea"}
              }
   :modals {:modal/feed-add    {:feed-add/visible false}
            :modal/feed-remove {:feed-remove/id nil}}})
