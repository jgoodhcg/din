(ns app.subscriptions
  (:require [re-frame.core :refer [reg-sub]]
            [com.rpl.specter :as sp :refer [select-one! select]]))

(defn version [db _]
  (->> db
       (select-one! [:version])))

(defn theme [db _]
  (->> db
       (select-one! [:settings :theme])))

(defn feeds [db _]
  (->> db
       (select [:feeds sp/MAP-VALS])))

(defn modal-feed-add-visible [db _]
  (->> db
       (select-one! [:modals :modal/feed-add :feed-add/visible])))

(reg-sub :version version)
(reg-sub :theme theme)
(reg-sub :feeds feeds)
(reg-sub :modal-feed-add-visible modal-feed-add-visible)
