(ns app.handlers
  (:require
   [re-frame.core :refer [reg-event-db
                          ->interceptor
                          reg-event-fx]]
   [com.rpl.specter :as sp :refer [setval transform select]]
   [clojure.spec.alpha :as s]
   [app.db :as db :refer [default-app-db app-db-spec]]))

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

(def base-interceptors  [;; (when ^boolean goog.DEBUG debug) ;; use this for some verbose re-frame logging
                         spec-validation])

(def id-gen
  (->interceptor :id :id-gen
                 :before #(assoc-in % [:coeffects :new-uuid] (random-uuid))))

(defn initialize-db [_ _]
  default-app-db)
(reg-event-db :initialize-db [base-interceptors] initialize-db)

(defn set-theme [db [_ theme]]
  (->> db
       (setval [:settings :theme] theme)))
(reg-event-db :set-theme [base-interceptors] set-theme)

(defn set-version [db [_ version]]
  (->> db
       (setval [:version] version)))
(reg-event-db :set-version [base-interceptors] set-version)

(defn some-fx-example [cofx [_ x]]
  {:db              (:db cofx)
   :some-fx-example x})
(reg-event-fx :some-fx-example [base-interceptors] some-fx-example)

(defn modal-toggle-feed-add [db [_ _]]
  (->> db (transform [:modals :modal/feed-add :feed-add/visible] not)))
(reg-event-db :modal-toggle-feed-add [base-interceptors] modal-toggle-feed-add)

(defn add-feed [{:keys [new-uuid db]} [_ feed-url]]
  {:db           (->> db
                      (transform [:feeds] #(assoc % new-uuid
                                                  {:feed/id  new-uuid
                                                   :feed/url feed-url})))
   :refresh-feed {:feed/id  new-uuid
                  :feed/url feed-url}})
(reg-event-fx :add-feed [base-interceptors id-gen] add-feed)

(defn update-feed [db [_ {:feed/keys [id] :as feed}]]
  (tap> {:location "update-feed handler"
         :feed     feed
         :db       db})
  (->> db (transform [:feeds (sp/keypath id)] #(merge % feed))))
(reg-event-db :update-feed [base-interceptors] update-feed)

(defn refresh-feeds [{:keys [db]} [_ _]]
  (tap> {:location "refresh-feeds handler"
         :feeds    (->> db (select [:feeds sp/MAP-VALS]))})
  {:db            db
   :refresh-feeds (->> db (select [:feeds sp/MAP-VALS]))})
(reg-event-fx :refresh-feeds [base-interceptors] refresh-feeds)
