(ns hangingfly.session-manager
  (:require [hangingfly.session :refer [make-session]]))

(declare add-session)

(defprotocol ISessionManager
  (new-session [this] [this attrs])
  (terminate-session [this sid])
  (valid-session? [this sid])
  (renew-session [this sid])
  (invalidate-sessions [this])
  (get-sessions [this])
  (set-session-attribute [this sid attrs]))

(defrecord SessionManager
  [session-store session-coll]
  ISessionManager
  (new-session
    [this]
    (let [session (make-session)]
      (swap! (:session-coll this) assoc (:session-id session) session)
      session))
  (terminate-session
    [this sid]
    (swap! (:session-coll this) dissoc sid))
  (valid-session?
    [this sid]
    (let [session (get @(:session-coll this) sid)]
      (:valid? session)))
  )

(defn make-session-manager
  ([]
   (make-session-manager nil))
  ([session-store]
   (make-session-manager session-store (atom {})))
  ([session-store session-coll]
   (->SessionManager session-store session-coll)))
