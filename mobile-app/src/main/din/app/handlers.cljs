(ns din.app.handlers
  (:require
   [re-frame.core :refer [reg-event-db
                          ->interceptor
                          reg-event-fx]]
   [com.rpl.specter :as sp :refer [setval transform select select-one!]]
   [clojure.spec.alpha :as s]
   [clojure.string :as st]
   [app.helpers :refer [screen-name->key]]
   [app.db :as db :refer [default-app-db app-db-spec]]
   [potpuri.core :as p]
   [potpuri.core :as pot]))

(defn check-and-throw
  "Throw an exception if db doesn't have a valid spec."
  [spec db event]
  (when-not (s/valid? spec db)
    (let [explanation (s/explain-str spec db)]
      (tap> {:spec-failure-event event
             :explanation explanation})
      (throw (str "Spec check failed: " explanation))
      true)))

(defn validate-spec [context]
  (let [db     (-> context :effects :db)
        old-db (-> context :coeffects :db)
        event  (-> context :coeffects :event)]

    (if (some? (check-and-throw app-db-spec db event))
      (assoc-in context [:effects :db] old-db)
      ;; put the old db back as the new db when check fails
      ;; otherwise return context unchanged
      context)))

(def spec-validation
(->interceptor
      :id :spec-validation
      :after (if goog.DEBUG
               validate-spec
               identity)))

(def base-interceptors  [;; (when ^boolean goog.DEBUG debug) ;; use this for some verbose re-frame logging
                         spec-validation])

(def id-gen
  (->interceptor :id :id-gen
                 :before #(assoc-in % [:coeffects :new-uuid] (random-uuid))))

(defn initialize-db [_ [_ version]]
  (println "init-db ----------------------------------------------------")
  {:db default-app-db})
(reg-event-fx :event/initialize-db [spec-validation] initialize-db)

(defn set-theme [db [_ theme]]
  (->> db
       (setval [:settings :theme] theme)))
(reg-event-db :event/set-theme [base-interceptors] set-theme)

(defn set-version [db [_ version]]
  (->> db
       (setval [:version] version)))
(reg-event-db :event/set-version [base-interceptors] set-version)

(defn modal-toggle-feed-add [db [_ _]]
  (->> db (transform [:modals :modal/feed-add :feed-add/visible] not)))
(reg-event-db :event/modal-toggle-feed-add [base-interceptors] modal-toggle-feed-add)

(defn add-feed [{:keys [new-uuid db]} [_ feed-url]]
  {:db                  (->> db
                             (transform [:feeds] #(assoc % new-uuid
                                                         {:feed/id  new-uuid
                                                          :feed/url feed-url})))
   :effect/refresh-feed {:feed/id  new-uuid
                         :feed/url feed-url}})
(reg-event-fx :event/add-feed [base-interceptors id-gen] add-feed)

(defn update-feed [cofx [_ {:feed/keys [id] :as feed}]]
  (tap> {:location :event/update-feed
         :feed     feed})
  (->> cofx
       (transform [:db :feeds (sp/keypath id)] #(merge % (dissoc feed
                                                                 :feed/items
                                                                 :feed/items-not-indexed)))
       (merge {:dispatch [:event/update-all-feed-items
                          (select-keys feed [:feed/id :feed/items-not-indexed])]})))
(reg-event-fx :event/update-feed [base-interceptors] update-feed)

(defn refresh-feeds [{:keys [db]} [_ _]]
  {:db                   db
   :effect/refresh-feeds (->> db (select [:feeds sp/MAP-VALS]))})
(reg-event-fx :event/refresh-feeds [base-interceptors] refresh-feeds)

(defn modal-open-feed-remove [db [_ feed-id]]
  (->> db (setval [:modals :modal/feed-remove :feed-remove/id] feed-id)))
(reg-event-db :event/modal-open-feed-remove [base-interceptors] modal-open-feed-remove)

(defn modal-close-feed-remove [db [_ _]]
  (->> db (setval [:modals :modal/feed-remove :feed-remove/id] nil)))
(reg-event-db :event/modal-close-feed-remove [base-interceptors] modal-close-feed-remove)

(defn remove-feed [db [_ feed-id]]
  ;; TODO set `:selected-feed/id`, `:selected-feed/item-id`, and `:selected-feed/items-status`
  ;; to nil if the first matches the removed feed-id
  (->> db
       (transform [:feeds] #(dissoc % feed-id))))
(reg-event-db :event/remove-feed [base-interceptors] remove-feed)

(defn trigger-load-db [cofx _]
  (println "trigger load app db handler ----------------------------")
  (merge cofx {:effect/load true
               ;; TODO 2022-07-11 Justin - Enable persisting
               ;; :effect/set-persist-handler true
               }))
(reg-event-fx :event/trigger-load-db trigger-load-db)

(defn load-app-db [_ [_ {:keys [app-db version]}]]
  (println "load app db handler ----------------------------")
  (let [{:selected-feed/keys [id item-id]}
        (->> app-db (select-one! [:selected (sp/submap [:selected-feed/id
                                                        :selected-feed/item-id])]))]

    (merge {:db         (p/deep-merge default-app-db app-db) ;; merge default to handle accretion changes without blowing up spec check
            :dispatch-n [[:event/set-version version]
                         [:event/refresh-feeds]
                         [:event/init-for-logged-in-user]]
            :effect/load-roam-credentials
            true}
           (when (and (some? id)
                      (some? item-id))
             {:effect/load-playback-object
              (->> app-db (select-one! [:feeds
                                        (sp/keypath id)
                                        :feed/items
                                        (sp/keypath item-id)
                                        (sp/submap [:feed-item/url
                                                    :feed-item/position])])
                   (merge {:feed/id      id
                           :feed-item/id item-id}))}))))
(reg-event-fx :event/load-app-db [spec-validation] load-app-db)

(defn select-feed [cofx [_ {:keys [feed-id navigate]}]]
  (tap> {:location "select feed handler"
         :feed-id  feed-id
         :navigate navigate})
  (merge (->> cofx (setval [:db :selected :selected-feed/id] feed-id))
         (when navigate {:effect/navigate :screen/feed})))
(reg-event-fx :event/select-feed [base-interceptors] select-feed)

(defn save-navigation [db [_ route-name]]
  (tap> {:location   "save-navigation"
         :route-name route-name
         :screen-key (screen-name->key route-name)})
  (->> db (setval [:navigation :navigation/last-screen] (screen-name->key route-name))))
(reg-event-db :event/save-navigation [base-interceptors] save-navigation)

;; TODO justin 2021-03-27 is this even needed? I can't figure out how to use this without running into a race condition with nav ref.
(defn go-to-last-screen [cofx _]
  (tap> {:location    "go-to-last-screen"
         :last-screen (->> cofx (select-one! [:db :navigation :navigation/last-screen]))})
  (->> cofx (merge {:effect/navigate (->> cofx (select-one! [:db :navigation :navigation/last-screen]))})))
(reg-event-fx :event/go-to-last-screen [base-interceptors] go-to-last-screen)

(defn select-feed-item [cofx [_ {feed-item-id :feed-item/id
                                 feed-id      :feed/id
                                 navigate     :navigate
                                 :as          params}]]

  (let [new-note-id (->> cofx
                         (select
                           [:db :feeds (sp/keypath feed-id)
                            :feed/items (sp/keypath feed-item-id)
                            :feed-item/notes sp/MAP-VALS :feed-item-note/id])
                         first)]

    (merge (->> cofx (setval [:db :selected :selected-feed/item-id] feed-item-id))

           (when navigate {:effect/navigate :screen/feed-item})

           (when (some? new-note-id)
             {:dispatch [:event/select-note {:feed/id           feed-id
                                             :feed-item/id      feed-item-id
                                             :feed-item-note/id new-note-id}]})
           {:effect/load-playback-object
            (->> cofx
                 (select-one! [:db
                               :feeds
                               (sp/keypath feed-id)
                               :feed/items
                               (sp/keypath feed-item-id)
                               (sp/submap [:feed-item/url
                                           :feed-item/position])])
                 (merge (select-keys params [:feed-item/id :feed/id])))})))
(reg-event-fx :event/select-feed-item [base-interceptors] select-feed-item)

(defn update-feed-item [db [_ {feed-item-id :feed-item/id
                               feed-id      :feed/id
                               feed-item    :feed-item
                               :as          params}]]
  (->> db (transform [:feeds
                      (sp/keypath feed-id)
                      :feed/items
                      (sp/keypath feed-item-id)]
                     #(merge % feed-item))))
(reg-event-db :event/update-feed-item ;; [base-interceptors]
              update-feed-item)

(defn update-all-feed-items [db [_ {feed-id    :feed/id
                                    feed-items :feed/items-not-indexed}]]
  (tap> {:location               :event/update-all-feed-items
         :feed/id                feed-id
         :feed/items-not-indexed feed-items})
  (loop [items feed-items
         d     db]
    (if (empty? items)
      d
      (let [{id :feed-item/id :as item} (last items)]
        (recur (butlast items)
               (update-feed-item d [nil {:feed-item/id id
                                         :feed/id      feed-id
                                         :feed-item    item}]))))))
(reg-event-db :event/update-all-feed-items [base-interceptors] update-all-feed-items)

(defn update-selected-item-status [db [_ {status :status}]]
  (->> db (setval [:selected :selected-feed/item-status] status)))
(reg-event-db :event/update-selected-item-status ;; [base-interceptors]
              update-selected-item-status)

(defn set-finished-override-all-items [db [_ {feed-id   :feed/id
                                              user-mark :feed-item/finished-override}]]
  (->> db (setval [:feeds (sp/keypath feed-id)
                   :feed/items sp/MAP-VALS
                   :feed-item/finished-override]
                  user-mark)))
(reg-event-db :event/set-finished-override-all-items [base-interceptors]
              set-finished-override-all-items)

(defn set-feed-item-sort [db [_ {feed-id   :feed/id
                                 item-sort :feed/item-sort}]]
  ;; TODO justin 2021-05-04 this needs to be set on refresh / update / and selection to keep the button in sync
  (->> db (setval [:feeds (sp/keypath feed-id)
                   :feed/item-sort] item-sort)))
(reg-event-db :event/set-feed-item-sort set-feed-item-sort)

(defn set-feed-item-filter [db [_ {feed-id     :feed/id
                                   item-filter :feed/item-filter}]]
  (->> db (setval [:feeds (sp/keypath feed-id)
                   :feed/item-filter] item-filter)))
(reg-event-db :event/set-feed-item-filter set-feed-item-filter)

(defn play-selected-item [cofx [_ _]]
  (merge cofx {:effect/play-selected-item true}))
(reg-event-fx :event/play-selected-item play-selected-item)

(defn pause-selected-item [cofx [_ _]]
  (merge cofx {:effect/pause-selected-item true}))
(reg-event-fx :event/pause-selected-item pause-selected-item)

(defn add-note [{:keys [new-uuid db]} [_ {feed-id  :feed/id
                                          item-id  :feed-item/id
                                          text     :feed-item-note/text
                                          position :feed-item-note/position
                                          :as      args}]]
  (tap> {:location :event/add-note
         :args args})
  {:db (->> db (setval [:feeds (sp/keypath feed-id)
                        :feed/items (sp/keypath item-id)
                        :feed-item/notes (sp/keypath new-uuid)]

                       {:feed-item-note/id       new-uuid
                        :feed-item-note/position position
                        :feed-item-note/text     text}))
   :dispatch [:event/select-note {:feed/id           feed-id
                                  :feed-item/id      item-id
                                  :feed-item-note/id new-uuid}]})
(reg-event-fx :event/add-note [base-interceptors id-gen] add-note)

(defn select-note [db [_ {feed-id :feed/id
                          item-id :feed-item/id
                          note-id :feed-item-note/id
                          :as args}]]
  {:location :event/select-note
   :args args}
  (->> db (transform [:selected] #(merge % {:selected-feed/id                    feed-id
                                            :selected-feed/item-id               item-id
                                            :selected-feed/item-selected-note-id note-id}))))
(reg-event-db :event/select-note [base-interceptors] select-note)

(defn seek-selected-item [{:keys [db]} [_ {offset       :seek/offset-millis
                                           abs-position :seek/absolute-position}]]
  (let [{feed-id :selected-feed/id
         item-id :selected-feed/item-id}
        (->> db (select-one! [:selected]))

        position
        (->> db (select-one! [:feeds (sp/keypath feed-id)
                              :feed/items (sp/keypath item-id)
                              :feed-item/position]))

        new-position (or abs-position (+ position offset))]

    {:db                                db
     :effect/set-position-selected-item new-position}))
(reg-event-fx :event/seek-selected-item seek-selected-item)

(defn update-selected-note-text [db [_ {text :feed-item-note/text}]]
  (let [{feed-id :selected-feed/id
         item-id :selected-feed/item-id
         note-id :selected-feed/item-selected-note-id}
        (->> db (select-one! [:selected]))]
    (->> db (setval [:feeds (sp/keypath feed-id)
                     :feed/items (sp/keypath item-id)
                     :feed-item/notes (sp/keypath note-id)
                     :feed-item-note/text] text))))
(reg-event-db :event/update-selected-note-text [base-interceptors] update-selected-note-text)

(defn cycle-selected-note [db [_ {direction :cycle/direction}]]
  (let [{feed-id :selected-feed/id
         item-id :selected-feed/item-id
         note-id :selected-feed/item-selected-note-id}
        (->> db (select-one! [:selected]))

        notes
        (->> db
             (select [:feeds (sp/keypath feed-id)
                      :feed/items (sp/keypath item-id)
                      :feed-item/notes sp/MAP-VALS])
             (sort-by :feed-item-note/position))

        old-note-index
        (-> notes (p/find-index #(= (:feed-item-note/id %) note-id)))

        extract (case direction
                  :cycle/prev last
                  :cycle/next second)

        ;; cycle notes so that the selected is first
        ;; going forwward (next) means taking the "second" item
        ;; going backwards (prev) means taking the "last" item
        ;; If ":b" is selected and we want the next item
        ;; [ :a :b :c ] -> cycling -> [:b :c :a] -> extraction -> :c
        new-note-id
        (->> notes

             ;; the cycling
             cycle
             (drop old-note-index)
             (take (count notes))

             ;; extraction
             extract
             :feed-item-note/id)]

    (->> db (setval [:selected :selected-feed/item-selected-note-id] new-note-id))))
(reg-event-db :event/cycle-selected-note [base-interceptors] cycle-selected-note)

(defn delete-selected-note [db _]
  (let [{feed-id :selected-feed/id
         item-id :selected-feed/item-id
         note-id :selected-feed/item-selected-note-id}
        (->> db (select-one! [:selected]))
        to-notes   [:feeds (sp/keypath feed-id)
                    :feed/items (sp/keypath item-id)
                    :feed-item/notes]
        note-count (->> db (select (conj to-notes sp/MAP-VALS)) count)]

    (-> db
        (cycle-selected-note [nil {:cycle/direction :cycle/prev}])
        (->> (setval (conj to-notes (sp/keypath note-id)) sp/NONE))
        (cond->> (-> note-count (<= 1))
          (setval [:selected :selected-feed/item-selected-note-id] nil)))))
(reg-event-db :event/delete-selected-note [base-interceptors] delete-selected-note)

(defn share-selected-note [cofx _]
  (let [{feed-id :selected-feed/id
         item-id :selected-feed/item-id
         note-id :selected-feed/item-selected-note-id}
        (->> cofx (select-one! [:db :selected]))

        params (->> cofx (select-one! [:db :feeds (sp/keypath feed-id)
                                       (sp/collect-one (sp/submap [:feed/title]))
                                       :feed/items (sp/keypath item-id)
                                       (sp/collect-one (sp/submap [:feed-item/title]))
                                       :feed-item/notes (sp/keypath note-id)
                                       (sp/submap [:feed-item-note/position
                                                   :feed-item-note/text])])
                    (apply merge))]
    (tap> {:location "share selected note handler"
           :params   params})
    (merge cofx {:effect/share params})))
(reg-event-fx :event/share-selected-note [base-interceptors] share-selected-note)

(defn reset-roam-pages [db [_ pages]]
  (->> db (setval [:roam-pages] pages)))
(reg-event-db :event/reset-roam-pages [base-interceptors] reset-roam-pages)

(defn set-playback-rate-menu-visible [db [_ visible]]
  (->> db (setval [:menus :menu/playback-rate :playback-rate/visible] visible)))
(reg-event-db :event/set-playback-rate-menu-visible [base-interceptors] set-playback-rate-menu-visible)

(defn set-playback-rate [cofx [_ {feed-id :feed/id
                                  rate    :feed/playback-rate}]]
  (tap> {:location :event/set-playback-rate
         :rate     rate})
  (->> cofx (setval [:db :feeds (sp/keypath feed-id) :feed/playback-rate] rate)
       (merge {:effect/set-playback-rate rate})))
(reg-event-fx :event/set-playback-rate [base-interceptors] set-playback-rate)

(defn set-auth-listener [cofx _]
  (println "Setting auth listener ----------------------------")
  (merge cofx {:effect/set-auth-listener true}))
(reg-event-fx :event/set-auth-listener [base-interceptors] set-auth-listener)

(defn init-for-logged-in-user [cofx _]
  (println "Initializing for potential logged in user ----------------------------")
  (merge cofx {:effect/init-for-logged-in-user true}))
(reg-event-fx :event/init-for-logged-in-user [base-interceptors] init-for-logged-in-user)

(defn set-stripe-data [db [_ stripe-data]]
  (->> db (setval [:stripe] stripe-data)))
(reg-event-db :event/set-stripe-data [base-interceptors] set-stripe-data)

(defn navigate [cofx [_ screen-id]]
  (merge cofx {:effect/navigate screen-id}))
(reg-event-fx :event/navigate [base-interceptors] navigate)

(defn go-to-subscription [cofx _]
  (merge cofx {:effect/navigate         :screen/subscription
               :effect/load-stripe-data true}))
(reg-event-fx :event/go-to-subscription [base-interceptors] go-to-subscription)

(comment
  (->> @re-frame.db/app-db
       (select-one! [:feeds
                     sp/FIRST ;; first key val pair
                     sp/LAST  ;; the val portion of that one pair
                     :feed/items
                     sp/FIRST ;; first key val pair
                     sp/LAST  ;; the val portion of that one pair
                     (sp/submap [:feed-item/url :feed-item/playback-position])]))
  )

(defn sign-in [cofx _]
  (let [credentials
        (->> cofx (select-one!
                   [:db :supabase
                    (sp/submap [:supabase/email :supabase/password])]))]
    (merge cofx {:effect/supabase-sign-in credentials})))
(reg-event-fx :event/sign-in [base-interceptors] sign-in)

(defn sign-up [cofx _]
  (let [{password :supabase/password
         confirm-password :supabase/confirm-password
         :as credentials}
        (->> cofx (select-one!
                   [:db :supabase
                    (sp/submap [:supabase/email
                                :supabase/password
                                :supabase/confirm-password])]))]
    (if (= password confirm-password)
      (->> cofx (setval [:effect/supabase-sign-up] credentials))
      (->> cofx (setval [:dispatch] [:event/set-sign-up-error "Passwords don't match"] )))))
(reg-event-fx :event/sign-up [base-interceptors] sign-up)

(defn sign-out [cofx _]
  (->> cofx
       (setval [:db :supabase :supabase/user] nil)
       (setval [:effect/supabase-sign-out] true)))
(reg-event-fx :event/sign-out [base-interceptors] sign-out)

(defn set-supabase-email [cofx [_ email]]
  (->> cofx
       (setval [:db :supabase :supabase/email] email)
       (setval [:dispatch] [:event/set-sign-up-error nil])
       (setval [:dispatch] [:event/set-sign-in-error nil])))
(reg-event-fx :event/set-supabase-email [base-interceptors] set-supabase-email)

(defn set-supabase-password [cofx [_ password]]
  (->> cofx
       (setval [:db :supabase :supabase/password] password)
       (setval [:dispatch] [:event/set-sign-up-error nil])
       (setval [:dispatch] [:event/set-sign-in-error nil])))
(reg-event-fx :event/set-supabase-password [base-interceptors] set-supabase-password)

(defn set-supabase-confirm-password [cofx [_ confirm-password]]
  (->> cofx
       (setval [:db :supabase :supabase/confirm-password] confirm-password)
       (setval [:dispatch] [:event/set-sign-up-error nil])
       (setval [:dispatch] [:event/set-sign-in-error nil])))
(reg-event-fx :event/set-supabase-confirm-password [base-interceptors] set-supabase-confirm-password)

(defn set-sign-in-error [cofx [_ error]]
  (cond->> cofx
    true          (setval [:db :supabase :supabase/sign-in-error] error)
    (some? error) (setval [:dispatch] [:event/sign-out])))
(reg-event-fx :event/set-sign-in-error [base-interceptors] set-sign-in-error)

(defn set-sign-up-error [cofx [_ error]]
  (cond->> cofx
    true          (setval [:db :supabase :supabase/sign-up-error] error)
    (some? error) (setval [:dispatch] [:event/sign-out])))
(reg-event-fx :event/set-sign-up-error [base-interceptors] set-sign-up-error)

(defn set-supabase-user [db [_ user]]
  (->> db (setval [:supabase :supabase/user] user)))
(reg-event-db :event/set-supabase-user [base-interceptors] set-supabase-user)

(defn set-keyboard-listener [cofx _]
 (println "setting keyboard listener ----------------------------")
  (merge cofx {:effect/set-keyboard-listener true}))
(reg-event-fx :event/set-keyboard-listener [base-interceptors] set-keyboard-listener)

(defn set-keyboard-showing [db [_ is-showing]]
  (->> db (setval [:misc :misc/keyboard-showing] is-showing)))
(reg-event-db :event/set-keyboard-showing [base-interceptors] set-keyboard-showing)

(defn set-note-selection [db [_ {start :note-selection/start
                                 end   :note-selection/end}]]
  (->> db (setval [:misc :misc/note-selection] {:note-selection/start start
                                                 :note-selection/end   end})))
(reg-event-db :event/set-note-selection [base-interceptors] set-note-selection)

(comment
  (->> @re-frame.db/app-db
       (select [:misc])
       tap>)
  )

(defn create-page-link-in-selected-note [{:keys [db] :as cofx} _]
  (let [{feed-id :selected-feed/id
         item-id :selected-feed/item-id
         note-id :selected-feed/item-selected-note-id}
        (->> db (select-one! [:selected]))
        text-path      [:feeds (sp/keypath feed-id)
                        :feed/items (sp/keypath item-id)
                        :feed-item/notes (sp/keypath note-id)
                        :feed-item-note/text]
        selection-path [:misc :misc/note-selection :note-selection/start]
        prev-note-text (->> db (select-one! text-path) (or ""))
        prev-selection (->> db (select-one! selection-path) (or 0))
        take-pos       prev-selection
        drop-pos       (-> prev-note-text count
                           (- (-> prev-selection (+ 2))))
        new-text       (-> prev-note-text
                           (->> (take take-pos))
                           vec
                           (conj " [[]] ")
                           (concat (->> prev-note-text (drop drop-pos)))
                           (->> (st/join "")))
        new-pos        (-> prev-selection (+ 3))]

    (tap> (p/map-of prev-note-text prev-selection new-text new-pos :create-page-link))

    (merge cofx
           {:effect/add-page-link-to-text-input
            {:note-selection/start new-pos
             :note-selection/end   new-pos
             :feed-item-note/text  new-text}})))
(reg-event-fx :event/create-page-link-in-selected-note [base-interceptors] create-page-link-in-selected-note)

(defn persist-app-db [{:keys [db]} _]
  (->> {:effect/persist db}))
(reg-event-fx :event/persist-app-db persist-app-db)
