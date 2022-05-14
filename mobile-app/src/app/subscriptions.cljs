(ns app.subscriptions
  (:require [re-frame.core :refer [reg-sub]]
            [com.rpl.specter :as sp :refer [select-one! select transform]]
            [app.helpers :refer [millis->str percent-of-duration]]
            [instaparse.core :as insta :refer-macros [defparser]]
            [potpuri.core :as p]
            [clojure.string :as s]))

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
                                 "sort-ascending")
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
                  #(->> % vals vec))

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

(defn selected-feed-item-selected-note-id [db _]
  (->> db (select-one! [:selected :selected-feed/item-selected-note-id])))
(reg-sub :sub/selected-feed-item-selected-note-id selected-feed-item-selected-note-id)

(defn selected-feed-item [[feeds-indexed
                           selected-feed-id
                           selected-feed-item-id
                           selected-feed-item-status
                           selected-note-id] _]
  (->> feeds-indexed
       (select-one! [(sp/keypath selected-feed-id)
                     (sp/collect-one (sp/submap [:feed/title :feed/id :feed/playback-rate]))
                     :feed/items
                     (sp/keypath selected-feed-item-id)])
       (transform [sp/LAST]
                  add-progress-bar-to-item)
       (transform [sp/LAST]
                  #(merge % {:feed-item/playback-status selected-feed-item-status}))
       ;; add left percentage to all notes
       (transform [sp/LAST (sp/collect (sp/submap [:feed-item/duration]))
                   :feed-item/notes sp/MAP-VALS]
                  ;; TODO justin 2021-04-28 consolidate with selected-feed sub
                  (fn [[{duration :feed-item/duration}]
                      {position :feed-item-note/position
                       :as      note}]
                    (merge note {:feed-item-note/left (percent-of-duration position duration)})))
       ;; ;; add selected note (relies on above :left being in place)
       (transform [sp/LAST]
                  (fn [item]
                    (let [selected-note
                          (-> item
                              :feed-item/notes
                              (get selected-note-id))]
                      (merge item {:feed-item/selected-note selected-note}))))
       ;; un-index notes
       (transform [sp/LAST :feed-item/notes] (fn [notes-indexed] (->> notes-indexed vals vec)))))
(reg-sub :sub/selected-feed-item
         :<- [:sub/feeds-indexed]
         :<- [:sub/selected-feed-id]
         :<- [:sub/selected-feed-item-id]
         :<- [:sub/selected-feed-item-status]
         :<- [:sub/selected-feed-item-selected-note-id]
         selected-feed-item)

(defn roam-pages [db _]
  (->> db (select [:roam-pages sp/ALL])))
(reg-sub :sub/roam-pages roam-pages)

(defn playback-rate-menu-visible [db _]
  (->> db (select-one! [:menus :menu/playback-rate :playback-rate/visible])))
(reg-sub :sub/playback-rate-menu-visible playback-rate-menu-visible)

(defn active-subscription-price-id [db _]
  (->> db (select-one! [:stripe :stripe/active-subscription :stripe.price/id])))
(reg-sub :sub/active-subscription-price-id active-subscription-price-id)

(defn possible-subscriptions [db _]
  (->> db (select-one! [:stripe :stripe/prices])))
(reg-sub :sub/possible-subscriptions possible-subscriptions)

(defn free-pass [db]
  (->> db (select-one! [:stripe :stripe/free-pass])))
(reg-sub :sub/free-pass free-pass)

(comment
  (->> @re-frame.db/app-db
       (select-one! [:menus :menu/playback-rate :playback-rate/visible]))

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

(defn supabase-credentials [db]
  (->> db (select-one! [:supabase (sp/submap [:supabase/email
                                              :supabase/password
                                              :supabase/confirm-password])])))
(reg-sub :sub/supabase-credentials supabase-credentials)

(defn supabase-sign-in-error [db]
  (->> db (select-one! [:supabase :supabase/sign-in-error])))
(reg-sub :sub/supabase-sign-in-error supabase-sign-in-error)

(defn supabase-sign-up-error [db]
  (->> db (select-one! [:supabase :supabase/sign-up-error])))
(reg-sub :sub/supabase-sign-up-error supabase-sign-up-error)

(defn supabase-user [db]
  (->> db (select-one! [:supabase :supabase/user])))
(reg-sub :sub/supabase-user supabase-user)

(defn keyboard-showing [db]
  (->> db (select-one! [:misc :misc/keyboard-showing]))
  )
(reg-sub :sub/keyboard-showing keyboard-showing)

(defn display-add-page-button
  [[selected-note-id keyboard-showing] _]
  (and (some? selected-note-id) (boolean keyboard-showing)))
(reg-sub :sub/display-add-page-button
         :<- [:sub/selected-feed-item-selected-note-id]
         :<- [:sub/keyboard-showing]
         display-add-page-button)

(defn note-selection [db]
  (->> db (select-one! [:misc :misc/note-selection])))
(reg-sub :sub/note-selection note-selection)

(comment
  (defn is-selection-within-brackets?
    [text pos]
    (println (p/map-of text pos))
    (let [result
          (if (-> pos (>= 2))
            (let [open                 (-> text (s/last-index-of "[[" (-> pos (- 1))))
                  maybe-breaking-close (-> text (s/last-index-of "]]" (-> pos (- 1))))]
              (println (p/map-of open maybe-breaking-close))
              (if (and (some? open)
                       (-> open (>= maybe-breaking-close))
                       (-> open (< (-> pos (- 1)))))
                (loop [p pos]
                  (let [open-along-the-way (-> text (s/index-of "[[" p))
                        maybe-close        (-> text (s/index-of "]]" p))]
                    (println (p/map-of p open-along-the-way maybe-close))
                    (cond
                      (nil? maybe-close) false
                      (-> maybe-close (< open-along-the-way)) true
                      :else (recur (-> open-along-the-way (+ 2))))))
                false))
            false)]

      (println "----------------")
      result))

  ;; (defn tester [i result] (println))
  (and
   (not (is-selection-within-brackets? "[[a]] [[ [[b/[[c]]]] [[" 0))
   (not (is-selection-within-brackets? "[[a]] [[ [[b/[[c]]]] [[" 1))
   (is-selection-within-brackets? "[[a]] [[ [[b/[[c]]]] [[" 2)
   (is-selection-within-brackets? "[[a]] [[ [[b/[[c]]]] [[" 3)
   (not (is-selection-within-brackets? "[[a]] [[ [[b/[[c]]]] [[" 4))
   (not (is-selection-within-brackets? "[[a]] [[ [[b/[[c]]]] [[" 5))
   (not (is-selection-within-brackets? "[[a]] [[ [[b/[[c]]]] [[" 6))
   (not (is-selection-within-brackets? "[[a]] [[ [[b/[[c]]]] [[" 7))
   (is-selection-within-brackets? "[[a]] [[ [[b/[[c]]]] [[" 8)
   (is-selection-within-brackets? "[[a]] [[ [[b/[[c]]]] [[" 9)
   (is-selection-within-brackets? "[[a]] [[ [[b/[[c]]]] [[" 10)
   (is-selection-within-brackets? "[[a]] [[ [[b/[[c]]]] [[" 18)
   (not (is-selection-within-brackets? "[[a]] [[ [[b/[[c]]]] [[" 19))

   )

  (defn suggested-roam-pages
    [[note-selection roam-pages selected-feed-item]]
    (let []))

  (reg-sub :sub/suggested-roam-pages
           :<- [:sub/note-selection]
           :<- [:sub/roam-pages]
           :<- [:sub/selected-feed-item]
           suggested-roam-pages))

(defparser structure-parser
  "text-or = ( code-block /
               code-span /
               page-link /
               braced-hashtag /
               naked-hashtag /
               block-ref /
               typed-block-ref /
               text-run )*
   (* below we need to list all significant groups in lookbehind + $ *)
   text-run = #'.+?(?=(\\[\\[|\\]\\]|#|\\(\\(|\\)\\)|$|\\`))\\n?'
   code-span = < '`' > text-or < '`' >
   code-block = < '```' >
                ( text-or | '\\n' )+
                < '```' >
   page-link = < double-square-open >
               ( text-till-double-square-close /
                 page-link /
                 braced-hashtag /
                 naked-hashtag )+
               < double-square-close >
   naked-hashtag = < hash > #'[^\\ \\+\\!\\@\\#\\$\\%\\^\\&\\*\\(\\)\\?\\\"\\;\\:\\]\\[]+'
   braced-hashtag = < hash double-square-open >
                    ( text-till-double-square-close /
                      page-link /
                      braced-hashtag /
                      naked-hashtag)+
                    < double-square-close >
   block-ref = < double-paren-open >
               text-till-double-paren-close
               < double-paren-close >
   < text-till-double-square-close > = #'[^\\n$\\[\\]\\#]+?(?=(\\]\\]|\\[\\[|#))'
   < text-till-double-paren-close > = #'[^\\s]+?(?=(\\)\\)))'
   typed-block-ref = < double-curly-open >
                     ref-type < #':\\s*' > block-ref
                     < double-curly-close >
   ref-type = #'[^:]+'
   hash = '#'
   double-square-open = '[['
   double-square-close = ']]'
   double-paren-open = '(('
   double-paren-close = '))'
   double-curly-open = '{{'
   double-curly-close = '}}'")

(defparser my-parser
 ;; "S = #'\\[\\[(.*?)\\]\\]'+"
  "text-or = ( page-link /
               braced-hashtag /
               naked-hashtag /
               text-run )*
   (* below we need to list all significant groups in lookbehind + $ *)
   text-run = #'.+?(?=(\\[\\[|\\]\\]|#|\\(\\(|\\)\\)|$|\\`))\\n?'
   page-link = < double-square-open >
               ( text-till-double-square-close /
                 page-link /
                 braced-hashtag /
                 naked-hashtag )+
               < double-square-close >
   naked-hashtag = < hash > #'[^\\ \\+\\!\\@\\#\\$\\%\\^\\&\\*\\(\\)\\?\\\"\\;\\:\\]\\[]+'
   braced-hashtag = < hash double-square-open >
                    ( text-till-double-square-close /
                      page-link /
                      braced-hashtag /
                      naked-hashtag)+
                    < double-square-close >
   < text-till-double-square-close > = #'[^\\n$\\[\\]\\#]+?(?=(\\]\\]|\\[\\[|#))'
   hash = '#'
   double-square-open = '[['
   double-square-close = ']]'"

  )

(comment

  (let [parse-result (insta/parse structure-parser "[[a]] [[ [[b/[[c]]]] [[" :total :true)]
    (println parse-result))

  (insta/parse my-parser "[[a]] [[ [[b/[[c]]]] [[" :total :true)
  #_[:text-or
    [:page-link "a"]
    [:text-run " "]
    [:text-run "[[ "]
    [:page-link "b/" [:page-link "c"]]
    [:text-run " "]
    [:text-run "[["]]

  (->> [:text-or
        [:page-link "a"]
        [:text-run " "]
        [:text-run "[[ "]
        [:page-link "b/" [:page-link "c"]]
        [:text-run " "]
        [:text-run "[["]]

   (clojure.walk/postwalk
    (fn [x]
      (cond
        (and (vector? x) (= :page-link (first x))) (conj x :page-link-close)
        :else                                      x)))

   (#(do (cljs.pprint/pprint %) %))

   ((fn [ast]
      (let [counter (atom {:i 0 :in-page false})
            acc     (atom [{:i 0 :in-page false}])]
        (->> ast
             (clojure.walk/postwalk
              (fn [x]
                (cond
                  (keyword? x)
                  (case x
                    :page-link
                    (do (swap! counter (fn [c] (-> c (update :i inc) (assoc :x x))))
                        (swap! acc conj @counter)
                        (swap! counter (fn [c] (-> c (update :i inc) (assoc :in-page true :x x))))
                        (swap! acc conj @counter)
                        nil)

                    :page-link-close
                    (do (swap! counter (fn [c] (-> c (update :i inc) (assoc :x x))))
                        (swap! acc conj @counter)
                        (swap! counter (fn [c] (-> c (update :i inc) (assoc :x x))))
                        (swap! acc conj @counter)
                        nil)

                    :text-or  nil
                    :text-run (when (-> @acc last :x (= :page-link-close))
                                (swap! counter (fn [c] (-> c (assoc :in-page false))))
                                (swap! acc
                                       (fn [a]
                                         (let [last-two
                                               (-> a
                                                   (subvec (-> a count (- 2)))
                                                   (->> (mapv #(assoc % :in-page false))))]
                                           (-> a
                                               butlast
                                               butlast
                                               (concat last-two)
                                               vec))))))

                  (string? x)
                  (do
                    (loop [n 1]
                      (if (-> n (> (count x)))
                        nil
                        (do (swap! counter (fn [c] (-> c (update :i inc) (assoc :x x :n n))))
                            (swap! acc conj @counter)
                            (recur (-> n (+ 1))))))
                    (swap! counter #(dissoc % :n))
                    nil)

                  :else nil))))
        @acc)))

   (#(do (cljs.pprint/pprint %) %))

   )
)

(println "-------------------")
