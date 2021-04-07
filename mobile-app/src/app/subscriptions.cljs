(ns app.subscriptions
  (:require [re-frame.core :refer [reg-sub]]
            [com.rpl.specter :as sp :refer [select-one! select transform]]
            [app.helpers :refer [millis->str percent-of-duration]]))

(defn version [db _]
  (->> db
       (select-one! [:version])))
(reg-sub :sub/version version)

(defn theme [db _]
  (->> db
       (select-one! [:settings :theme])))
(reg-sub :sub/theme theme)

(defn feeds-indexed [db _]
  (->> db (select-one! [:feeds])))
(reg-sub :sub/feeds-indexed feeds-indexed)

(defn feeds [indexed-feeds _]
  (->> indexed-feeds
       (select [sp/MAP-VALS])))
(reg-sub :sub/feeds
         :<- [:sub/feeds-indexed]
         feeds)

(defn modal-feed-add-visible [db _]
  (->> db
       (select-one! [:modals :modal/feed-add :feed-add/visible])))
(reg-sub :sub/modal-feed-add-visible modal-feed-add-visible)

(defn modal-feed-remove-id [db _]
  (->> db
       (select-one! [:modals :modal/feed-remove :feed-remove/id])))
(reg-sub :sub/modal-feed-remove-id modal-feed-remove-id)

(defn modal-feed-remove-title [[maybe-feed-id feeds-indexed] _]
  (when-some [feed-id maybe-feed-id]
    (->> feeds-indexed (select-one! [(sp/keypath feed-id) :feed/title]))))
(reg-sub :sub/modal-feed-remove-title
         :<- [:sub/modal-feed-remove-id]
         :<- [:sub/feeds-indexed]
         modal-feed-remove-title)

(defn selected-feed-id [db _]
  (->> db (select-one! [:selected :selected-feed/id])))
(reg-sub :sub/selected-feed-id selected-feed-id)

(defn selected-feed [[feeds-indexed selected-feed-id] _]
  (->> feeds-indexed
       (select-one! [(sp/keypath selected-feed-id)])

       (transform [:feed/items sp/MAP-VALS]
                  (fn [{:feed-item/keys [position duration]
                       :as             item}]
                    (let [progress-width (percent-of-duration
                                           position
                                           duration)]
                      (-> item
                          (merge
                            #:feed-item {:progress-width progress-width
                                         :duration-str   (millis->str duration)
                                         :position-str   (millis->str position)})))))
       ;; TODO collect duration
       (transform [:feed/items sp/MAP-VALS
                   (sp/collect (sp/submap [:feed-item/duration]))
                   :feed-item/notes sp/MAP-VALS]
                  (fn [[{duration :feed-item/duration}
                       {:feed-item-note/keys [position]
                        :as                  note}]]
                    (-> note
                        (merge
                          {:feed-item-note/left
                           (percent-of-duration
                             position
                             duration)}))))
       (transform
         [:feed/items]
         #(->> % vals ;; this "un-indexes" the items
               (sort-by :feed-item/published)
               reverse))))

(comment
  (->> {:feed/items
        {1 {:feed-item/duration 10
            :feed-item/notes
            {1 {:feed-item-note/position 4}
             2 {:feed-item-note/position 5}}} }}
       (transform [:feed/items
                   sp/MAP-VALS
                   (sp/collect (sp/submap [:feed-item/duration]))
                   :feed-item/notes
                   sp/MAP-VALS]
                  (fn [[{duration :feed-item/duration}]
                      {position :feed-item-note/position
                       :as      note}]
                    (merge note {:feed-item-note/left (+ position duration)}))
                  )
       ;; (map (fn [[[{duration :feed-item/duration}] {position :feed-item-note/position}]]
       ;;        [duration position]))
       )
  )
(reg-sub :sub/selected-feed
         :<- [:sub/feeds-indexed]
         :<- [:sub/selected-feed-id]
         selected-feed)

(defn last-screen [db _]
  (->> db (select-one! [:navigation :navigation/last-screen])))
(reg-sub :sub/last-screen last-screen)

(defn selected-feed-item-id [db _]
  (->> db (select-one! [:selected :selected-feed/item-id])))
(reg-sub :sub/selected-feed-item-id selected-feed-item-id)

(defn selected-feed-item [[feeds-indexed
                           selected-feed-id
                           selected-feed-item-id] _]
  (->> feeds-indexed
       (select-one! [(sp/keypath selected-feed-id)
                     (sp/collect-one (sp/submap [:feed/title]))
                     :feed/items
                     (sp/keypath selected-feed-item-id)])))
(reg-sub :sub/selected-feed-item
         :<- [:sub/feeds-indexed]
         :<- [:sub/selected-feed-id]
         :<- [:sub/selected-feed-item-id]
         selected-feed-item)
