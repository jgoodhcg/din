(ns app.db
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.data-spec :as ds]))

(def feed-data-spec
  (ds/spec {:name ::feed-ds
            :spec {:feed/id           uuid?
                   :feed/url          string?
                   :feed/parsed-model map}}))

(s/def ::feed feed-data-spec)

(s/def ::feeds (s/and map? (s/every-kv uuid? ::feed)))

(def app-db-spec
  (ds/spec {:spec {:settings {:theme (s/spec #{:light :dark})}
                   :version  string?
                   :feeds    ::feeds
                   :modals   {:modal/feed-add {:feed-add/visible boolean?}}}
            :name ::app-db}))

(def default-app-db
  {:settings {:theme :dark}
   :version  "version-not-set"
   :feeds    {}
   :modals   {:modal/feed-add {:feed-add/visible false}}})
