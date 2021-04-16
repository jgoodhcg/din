(ns app.subscriptions
  (:require [re-frame.core :refer [reg-sub]]
            [com.rpl.specter :as sp :refer [select-one! select transform]]
            [app.helpers :refer [millis->str percent-of-duration]]
            [potpuri.core :as p]))

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

(defn add-progress-bar-to-item
  [{:feed-item/keys [position duration]
    :as             item}]
  (let [progress-width (percent-of-duration
                         position
                         duration)]
    (-> item
        (merge
          #:feed-item {:progress-width progress-width
                       :started        (-> position (> 0))
                       :duration-str   (millis->str duration)
                       :position-str   (millis->str position)}))))

(defn selected-feed [[feeds-indexed selected-feed-id] _]
  (->> feeds-indexed
       (select-one! [(sp/keypath selected-feed-id)])
       ;; Adds sort and filter icon
       (transform [] (fn [{:feed/keys [item-sort item-filter] :as feed}]
                       (merge feed
                              {:feed/item-sort-icon
                               (case item-sort
                                 :item-sort/ascending  "sort-ascending"
                                 :item-sort/descending "sort-descending"
                                 "sort-descending")
                               :feed/item-filter-icon
                               (case item-filter
                                 :item-filter/finished "filter"
                                 "filter-outline")})))
       ;; Adds progress bar items
       (transform [:feed/items sp/MAP-VALS]
                  add-progress-bar-to-item)

       ;; Adds note positioning info
       (transform [:feed/items sp/MAP-VALS
                   (sp/collect (sp/submap [:feed-item/duration]))
                   :feed-item/notes sp/MAP-VALS]
                  (fn [[{duration :feed-item/duration}]
                      {:feed-item-note/keys [position]
                       :as                  note}]
                    (-> note
                        (merge
                          {:feed-item-note/left
                           (percent-of-duration
                             position
                             duration)}))))

       ;; "un-indexes" notes
       (transform [:feed/items sp/MAP-VALS
                   :feed-item/notes]
                  #(->> % vals))

       ;; "un-indexes", sorts, and filters feed items
       (transform
         [(sp/submap [:feed/items :feed/item-sort :feed/item-filter])]
         (fn [{items       :feed/items
              item-sort   :feed/item-sort
              item-filter :feed/item-filter}]
           {:feed/items       (->> items
                                   vals
                                   (remove (fn [{:feed-item/keys [finished-override
                                                                 playback-position
                                                                 duration]}]
                                             (and (= item-filter :item-filter/finished)
                                                  (or (= finished-override :user-override/finished)
                                                      (-> playback-position (/ duration) (> 0.95))))))
                                   (sort-by :feed-item/published)
                                   ((fn [items]
                                      (if (= :item-sort/descending item-sort)
                                        items
                                        (reverse items))))
                                   vec)
            :feed/item-sort   item-sort
            :feed/item-filter item-filter}))))
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

(defn selected-feed-item-status [db _]
  (->> db (select-one! [:selected :selected-feed/item-status])))
(reg-sub :sub/selected-feed-item-status selected-feed-item-status)

(defn selected-feed-item [[feeds-indexed
                           selected-feed-id
                           selected-feed-item-id
                           selected-feed-item-status] _]
  (->> feeds-indexed
       (select-one! [(sp/keypath selected-feed-id)
                     (sp/collect-one (sp/submap [:feed/title :feed/id]))
                     :feed/items
                     (sp/keypath selected-feed-item-id)])
       (transform [sp/LAST]
                  add-progress-bar-to-item)
       (transform [sp/LAST]
                  #(merge % {:feed-item/playback-status selected-feed-item-status}))))
(reg-sub :sub/selected-feed-item
         :<- [:sub/feeds-indexed]
         :<- [:sub/selected-feed-id]
         :<- [:sub/selected-feed-item-id]
         :<- [:sub/selected-feed-item-status]
         selected-feed-item)

(comment
  (->> @re-frame.db/app-db
       (transform [:feeds sp/MAP-VALS :feed/items sp/MAP-VALS]
                  (fn [item] (merge item
                                   {:feed-item/position (rand-int 100)
                                    :feed-item/duration 100
                                    :feed-item/notes
                                    (->> (range 10)
                                         (map (fn [_]
                                                (let [id (random-uuid)]
                                                  {id #:feed-item-note {:position (rand-int 100)
                                                                        :text     "test note"
                                                                        :id       id}})))
                                         (apply merge))
                                    })))
       (reset! re-frame.db/app-db)
       (tap>)
       )

  (->> {:feeds {1 {:feed/items {1 {:feed-item/duration 100
                                   :feed-item/notes    {1 {:position 10}}}}}}}
       (transform [:feeds sp/MAP-VALS :feed/items sp/MAP-VALS
                   (sp/collect (sp/submap [:feed-item/duration]))
                   :feed-item/notes sp/MAP-VALS]
                  (fn [a b] (tap> {:a a
                                  :b b}))))
  )
