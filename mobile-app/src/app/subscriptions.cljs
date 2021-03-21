(ns app.subscriptions
  (:require [re-frame.core :refer [reg-sub]]
            [com.rpl.specter :as sp :refer [select-one! select]]))

(defn version [db _]
  (->> db
       (select-one! [:version])))
(reg-sub :version version)

(defn theme [db _]
  (->> db
       (select-one! [:settings :theme])))
(reg-sub :theme theme)

(defn feeds-indexed [db _]
  (->> db (select-one! [:feeds])))
(reg-sub :feeds-indexed feeds-indexed)

(defn feeds [indexed-feeds _]
  (->> indexed-feeds
       (select [sp/MAP-VALS])))
(reg-sub :feeds
         :<- [:feeds-indexed]
         feeds)

(defn modal-feed-add-visible [db _]
  (->> db
       (select-one! [:modals :modal/feed-add :feed-add/visible])))
(reg-sub :modal-feed-add-visible modal-feed-add-visible)

(defn modal-feed-remove-id [db _]
  (->> db
       (select-one! [:modals :modal/feed-remove :feed-remove/id])))
(reg-sub :modal-feed-remove-id modal-feed-remove-id)

(defn modal-feed-remove-title [[maybe-feed-id feeds-indexed] _]
  (when-some [feed-id maybe-feed-id]
    (->> feeds-indexed (select-one! [(sp/keypath feed-id) :feed/title]))))
(reg-sub :modal-feed-remove-title
         :<- [:modal-feed-remove-id]
         :<- [:feeds-indexed]
         modal-feed-remove-title)

(defn selected-feed-id [db _]
  (->> db (select-one! [:selected-feed])))
(reg-sub :selected-feed-id selected-feed-id)

(defn selected-feed [[feeds-indexed selected-feed-id] _]
  (->> feeds-indexed (select-one! [(sp/keypath selected-feed-id)])))
(reg-sub :selected-feed
         :<- [:feeds-indexed]
         :<- [:selected-feed-id]
         selected-feed)
