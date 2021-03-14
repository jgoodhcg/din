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
   :feeds    {#uuid "e391e5f9-2d87-43e9-b64e-2ae2df13b475"
              {:feed/url "https://feeds.transistor.fm/the-indie-hackers-podcast"
               :feed/id  #uuid "e391e5f9-2d87-43e9-b64e-2ae2df13b475" }}
   :modals   {:modal/feed-add    {:feed-add/visible false}
              :modal/feed-remove {:feed-remove/id nil}}})
