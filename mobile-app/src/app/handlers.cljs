(ns app.handlers
  (:require
   [re-frame.core :refer [reg-event-db
                          ->interceptor
                          reg-event-fx]]
   [com.rpl.specter :as sp :refer [setval transform select select-one!]]
   [clojure.spec.alpha :as s]
   [app.helpers :refer [screen-name->key]]
   [app.db :as db :refer [default-app-db app-db-spec]]
   [potpuri.core :as p]))

(defn check-and-throw
  "Throw an exception if db doesn't have a valid spec."
  [spec db event]
  (when-not (s/valid? spec db)
    (tap> {:spec-failure-event event})
    (let [explanation (s/explain-str spec db)]
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
  (if goog.DEBUG
    (->interceptor
      :id :spec-validation
      :after validate-spec)
    ->interceptor))

(def persist
  (->interceptor
    :id :persist
    :after (fn [context]
             (->> context (setval [:effects :effect/persist]
                                  (-> context :effects :db str))))))

(def base-interceptors  [persist
                         ;; (when ^boolean goog.DEBUG debug) ;; use this for some verbose re-frame logging
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
       (transform [:db :feeds (sp/keypath id)] #(merge % (dissoc feed :feed/items)))
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
  (merge cofx {:effect/load true}))
(reg-event-fx :event/trigger-load-db trigger-load-db)

(defn load-app-db [_ [_ {:keys [app-db version]}]]
  (println "load app db handler ----------------------------")
  {:db         (merge default-app-db app-db) ;; merge default to handle accretion changes without blowing up spec check
   :dispatch-n [[:event/set-version version]
                [:event/refresh-feeds]]})
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
  (merge (->> cofx (setval [:db :selected :selected-feed/item-id] feed-item-id))

         (when navigate {:effect/navigate :screen/feed-item})

         {:effect/load-playback-object
          (->> cofx
               (select-one! [:db
                             :feeds
                             (sp/keypath feed-id)
                             :feed/items
                             (sp/keypath feed-item-id)
                             (sp/submap [:feed-item/url
                                         :feed-item/playback-position])])
               (merge (select-keys params [:feed-item/id :feed/id])))}))
(reg-event-fx :event/select-feed-item [base-interceptors] select-feed-item)

(defn update-feed-item [db [_ {feed-item-id :feed-item/id
                               feed-id      :feed/id
                               feed-item    :feed-item
                               :as          params}]]

  (tap> {:location :event/update-feed-item
         :params   params})
  (->> db (transform [:feeds
                      (sp/keypath feed-id)
                      :feed/items
                      (sp/keypath feed-item-id)]
                     #(merge % feed-item))))
(reg-event-db :event/update-feed-item [base-interceptors] update-feed-item)

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
  (tap> {:location :event/update-selected-item-status
         :status   status})
  (->> db (setval [:selected :selected-feed/item-status] status)))
(reg-event-db :event/update-selected-item-status [base-interceptors] update-selected-item-status)

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
