(ns app.subscriptions
  (:require ["fuzzy" :as fuzzy]

            [clojure.string :as s]
            [clojure.walk :refer [postwalk]]

            [com.rpl.specter :as sp :refer [select-one! select transform]]
            [instaparse.core :as insta :refer-macros [defparser]]
            [potpuri.core :as p]
            [re-frame.core :refer [reg-sub]]

            [app.helpers :refer [millis->str percent-of-duration]]
            [applied-science.js-interop :as j]))

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

(defparser my-parser
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

(defn add-closing-page-links
  [ast]
  (->> ast
       (postwalk
        (fn [x]
          (cond
            (and (vector? x) (= :page-link (first x)))
            (conj x :page-link-close)

            (and (vector? x) (= :braced-hashtag (first x)))
            (conj x :page-link-close)

            (and (vector? x) (= :naked-hashtag (first x)))
            (conj x :naked-hashtag-psuedo-close)

            :else x)))))

(defn build-index
  [ast]
  (let [counter (atom {:i 0 :in-page false})
        acc     (atom [{:i 0 :in-page false}])]
    (->> ast
         (postwalk
          (fn [x]
            (cond
              (keyword? x)
              (case x
                ;; this _shouldn't_ happen
                ;; but if it does it is nice to fail gracefully
                ;; rather than blow up on "no matching case"
                :instaparse/failure         nil
                :text-or                    nil

                :page-link ;; [[ => 2
                (do (swap! counter (fn [c] (-> c (update :i inc) (assoc :x x))))
                    (swap! acc conj @counter)
                    (swap! counter (fn [c] (-> c (update :i inc) (assoc :in-page true :x x))))
                    (swap! acc conj @counter)
                    nil)

                :braced-hashtag ;; #[[ => 3
                (do (swap! counter (fn [c] (-> c (update :i inc) (assoc :x x))))
                    (swap! acc conj @counter)
                    (swap! counter (fn [c] (-> c (update :i inc) (assoc :x x))))
                    (swap! acc conj @counter)
                    (swap! counter (fn [c] (-> c (update :i inc) (assoc :in-page true :x x))))
                    (swap! acc conj @counter)
                    nil)

                :naked-hashtag ;; # => 1
                (do (swap! counter (fn [c] (-> c (update :i inc) (assoc :in-page true :x x))))
                    (swap! acc conj @counter)
                    nil)

                :page-link-close ;; ]] => 2
                (do (swap! counter (fn [c] (-> c (update :i inc) (assoc :x x))))
                    (swap! acc conj @counter)
                    (swap! counter (fn [c] (-> c (update :i inc) (assoc :x x))))
                    (swap! acc conj @counter)
                    nil)

                :naked-hashtag-psuedo-close ;; no incrementing
                ;; this is really just for :text-run to "close" the counter
                ;; in the group-by this results in the same index as the corresponding naked-hashtag and is a no-op
                (let [c (-> @counter (assoc :x x))]
                  (swap! acc conj c)
                  nil)

                :text-run (let [last-k (-> @acc last :x)]
                            (when (or (= last-k :page-link-close)
                                      (= last-k :naked-hashtag-psuedo-close))
                              ;; for either closing key we want to swap the counter to not be in a page
                              (swap! counter (fn [c] (-> c (assoc :in-page false))))
                              ;; only for page link close do we need to alter the last two indexes "]]" to not be in a page
                              (when (= last-k :page-link-close)
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
                                               vec))))))))

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
    (->> @acc (group-by :i) (into (sorted-map)))))

(defn determine-suggestion-hint
  [cursor-pos grouped-indexes]
  (let [{:keys [in-page x]} (-> grouped-indexes (get cursor-pos) first)]
    (when in-page
      (loop [maybe-link-text x
             i cursor-pos]
        (if (and (keyword? maybe-link-text)
                 (-> i (<= (-> grouped-indexes keys count))))
          ;; TODO need to go backwards as well
          (recur (-> grouped-indexes (get i) first :x) (+ 1 i))
          maybe-link-text)))))

(defn suggested-roam-pages
  [[note-selection roam-pages selected-feed-item]]
  (let [text (-> selected-feed-item
                 second
                 :feed-item/selected-note
                 :feed-item-note/text
                 ;; replace newlines since they cause `:instaparse/failure`s
                 (#(when (some? %) (clojure.string/replace % "\n" " "))))
        pos  (-> note-selection :note-selection/start)
        hint (when (and (some? text)
                        (some? pos))
               (->> (insta/parse my-parser text :total true)
                    add-closing-page-links
                    build-index
                    (determine-suggestion-hint pos)))]
    (when (some? hint)
      (-> fuzzy
          (j/call :filter hint (-> roam-pages clj->js))
          (js->clj :keywordize-keys true)
          (->> (sort-by :score))
          (->> (mapv :string))))))
(reg-sub :sub/suggested-roam-pages
           :<- [:sub/note-selection]
           :<- [:sub/roam-pages]
           :<- [:sub/selected-feed-item]
           suggested-roam-pages)

(defparser athens-parser
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

(comment
  (suggested-roam-pages
   [{:note-selection/start 3}
    ["a" "aa" "aaa" "aaab" "bbbb" "cccc"]
    [:nil {:feed-item/selected-note {:feed-item-note/text "x[[b\n]] \n"}}]])

  (suggested-roam-pages
   [{:note-selection/start 12}
    ["a" "aa" "aaa" "aaab" "bbbb" "cccc"]
    [:nil {:feed-item/selected-note {:feed-item-note/text "x[[b]] \n [[c]]"}}]])

  (->> (insta/parse my-parser "abc [[123]]\n [[abc]]" :total true))

  (->> (insta/parse athens-parser "abc [[123]]\n [[abc]]" :total true))

  (-> "x[[b]] \n #[[c/[[csharp]]]] #d extra ]]"
       (s/replace "\n" "_")
       (#(insta/parse my-parser % :total true))
       add-closing-page-links
       build-index)

(suggested-roam-pages
   [{:note-selection/start 18}
    ["a" "aa" "aaa" "aaab" "bbbb" "cccc" "abad"]
    [:nil {:feed-item/selected-note {:feed-item-note/text "x[[b]] \n #[[c]] #d extra ]]"}}]])

  )
