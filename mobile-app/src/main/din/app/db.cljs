(ns din.app.db
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.data-spec :as ds]))

(def feed-item-note-data-spec
  (ds/spec {:name ::feed-item-note-ds
            :spec {:feed-item-note/position integer?
                   :feed-item-note/text     string?
                   :feed-item-note/id       uuid?}}))

(s/def ::feed-item-note feed-item-note-data-spec)

(s/def ::feed-item-notes (s/and map? (s/every-kv uuid? ::feed-item-note)))

(def feed-item-data-spec
  (ds/spec {:name ::feed-item-ds
            :spec {:feed-item/id                         string?
                   :feed-item/title                      string?
                   :feed-item/image-url                  (ds/maybe string?)
                   :feed-item/description                (ds/maybe string?)
                   (ds/opt :feed-item/playback-position) integer?
                   (ds/opt :feed-item/duration)          integer?
                   (ds/opt :feed-item/notes)             ::feed-item-notes
                   (ds/opt :feed-item/finished-override) (s/spec #{:user-override/finished
                                                                   :user-override/unfinished})}}))

(s/def ::feed-item feed-item-data-spec)

(s/def ::feed-items (s/and map? (s/every-kv string? ::feed-item)))

(def feed-data-spec
  (ds/spec {:name ::feed-ds
            :spec {:feed/id                     uuid?
                   :feed/url                    string?
                   (ds/opt :feed/title)         string?
                   (ds/opt :feed/image-url)     string?
                   (ds/opt :feed/items)         ::feed-items
                   (ds/opt :feed/item-sort)     (s/spec #{:item-sort/ascending
                                                         :item-sort/descending})
                   (ds/opt :feed/item-filter)   (ds/maybe (s/spec #{:item-filter/finished}))
                   (ds/opt :feed/playback-rate) (s/spec #{0.5 0.75 1 1.5 2})}}))

(s/def ::feed feed-data-spec)

(s/def ::feeds (s/and map? (s/every-kv uuid? ::feed)))

(def app-db-spec
  (ds/spec {:spec {:settings   {:theme (s/spec #{:light :dark})}
                   :version    string?
                   :feeds      ::feeds
                   ;; TODO justin 2021-05-17 Change all :*/visible to "visibility"
                   :modals     {:modal/feed-add    {:feed-add/visible boolean?}
                                :modal/feed-remove {:feed-remove/id (ds/maybe uuid?)}}
                   :menus      {:menu/playback-rate {:playback-rate/visible boolean?}}
                   :misc       {:misc/keyboard-showing boolean?
                                :misc/note-selection   (ds/maybe {:note-selection/start integer?
                                                                  :note-selection/end   integer?})}
                   :selected   {:selected-feed/id                    (ds/maybe uuid?)
                                :selected-feed/item-id               (ds/maybe string?)
                                :selected-feed/item-status           (ds/maybe (s/spec #{:status/playing
                                                                                         :status/loading
                                                                                         :status/paused
                                                                                         :status/stopped}))
                                :selected-feed/item-selected-note-id (ds/maybe uuid?)}
                   :roam-pages [string?]
                   :stripe     (ds/maybe {:stripe/active-subscription (ds/maybe {:stripe.price/id string?})
                                          :stripe/free-pass           (ds/maybe string?)
                                          :stripe/prices              [{:stripe.price/id            string?
                                                                        :stripe.price/unit-amount   integer?
                                                                        :stripe.product/name        string?
                                                                        :stripe.product/images      [string?]
                                                                        :stripe.product/description (ds/maybe string?)}]})
                   :supabase   {:supabase/sign-in-error    (ds/maybe string?)
                                :supabase/sign-up-error    (ds/maybe string?)
                                :supabase/user             (ds/maybe map?)
                                :supabase/email            (ds/maybe string?)
                                :supabase/password         (ds/maybe string?)
                                :supabase/confirm-password (ds/maybe string?)}
                   :navigation {:navigation/last-screen (ds/maybe keyword?)}}
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

              ;; This feed is problematic
              ;; ;; newsletter crew
              ;; #uuid "79c90f1c-cbf6-435c-9eba-4066bef19fd0"
              ;; {:feed/url "https://newslettercrew.com/rss/"
              ;;  :feed/id  #uuid "79c90f1c-cbf6-435c-9eba-4066bef19fd0"}

              ;; run with it
              #uuid "4f9323dd-f309-49b1-99d9-2b88cc95e1ed"
              {:feed/url "https://feeds.transistor.fm/run-with-it"
               :feed/id  #uuid "4f9323dd-f309-49b1-99d9-2b88cc95e1ed"}

              ;; software social
              #uuid "cca6a4b3-23aa-4055-a72f-0286108492ea"
              {:feed/url "https://feeds.transistor.fm/software-social"
               :feed/id  #uuid "cca6a4b3-23aa-4055-a72f-0286108492ea"}
              }
   :modals     {:modal/feed-add    {:feed-add/visible false}
                :modal/feed-remove {:feed-remove/id nil}}
   :menus      {:menu/playback-rate {:playback-rate/visible false}}
   :misc       {:misc/keyboard-showing false
                :misc/note-selection nil}
   :selected   {:selected-feed/id                    nil
                :selected-feed/item-id               nil
                :selected-feed/item-status           nil
                :selected-feed/item-selected-note-id nil}
   :roam-pages []
   :navigation {:navigation/last-screen nil}
   :stripe     nil
   :supabase   {:supabase/sign-in-error    nil
                :supabase/sign-up-error    nil
                :supabase/user             nil
                :supabase/email            nil
                :supabase/password         nil
                :supabase/confirm-password nil}})
