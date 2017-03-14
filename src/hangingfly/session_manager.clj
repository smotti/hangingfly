(ns hangingfly.session-manager
  (:require [hangingfly.session :refer [make-session]]
            [hangingfly.session-repository.core :refer :all]
            [hangingfly.session-repository.atom :refer [->SessionRepository]]))

(defprotocol ISessionManager
  (new-session [this] [this attrs])
  (terminate-session [this sid])
  (valid-session? [this sid])
  (renew-session [this sid])
  (invalidate-session [this sid])
  (invalidate-sessions [this])
  (get-sessions [this])
  (set-session-attribute [this sid attrs]))

(defrecord SessionManager
  [session-repo]
  ISessionManager

  (new-session
    [this]
    (let [session (make-session)]
      (add-session (:session-repo this) session)
      session))
  (terminate-session
    [this sid]
    (remove-session (:session-repo this) sid))
  (valid-session?
    [this sid]
    (let [session (get-session (:session-repo this) sid)]
      (:valid? session false)))
  ; REVIEW: Would it be beneficial to make :previous-session-id a vector that
  ;         contains all the previous session id up to this point?
  ; REVIEW: And also add to the old session info about the new one!?
  (renew-session
    [this sid]
    (when-let [session (get-session (:session-repo this) sid)]
      (let [attrs {:previous-session-id sid}
            new-session (make-session attrs)]
        (invalidate-session this sid)
        (add-session (:session-repo this) new-session)
        new-session)))
  (invalidate-session
    [this sid]
    (when-let [session (get-session (:session-repo this) sid)] 
      (let [invalidated-session (assoc session
                                       :valid? false
                                       :end-time (System/currentTimeMillis))]
        (update-session (:session-repo this) invalidated-session)
        invalidated-session)))
  (invalidate-sessions
    [this]
    nil)
  )

(defn make-session-manager
  ([]
   (make-session-manager (->SessionRepository (atom {}))))
  ([session-repo]
   (->SessionManager session-repo)))
