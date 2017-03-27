(ns hangingfly.session-manager
  (:require [hangingfly.session-management :as mgmt]
            [hangingfly.session-repository.core :refer [get-session]]
            [hangingfly.session-repository.atom :refer [make-session-repository]]))

(defprotocol ISessionManager
  (invalidate-sessions [this session-ch duration query])
  (new-session [this] [this attrs])
  (renew-session [this sid])
  (renew-sessions [this session-ch duration query])
  (session-timeout? [this sid] [this sid timeout])
  (session-valid? [this sid])
  (stop-mgmt-task [this session-ch stop-ch])
  (terminate-session [this sid]))

(defrecord SessionManager [repository]
  ISessionManager

  (invalidate-sessions
    [this session-ch duration query]
    (mgmt/invalidate-sessions (:repository this) session-ch duration query))
  
  (new-session
    [this]
    (mgmt/new-session (:repository this)))

  (new-session
    [this attrs]
    (mgmt/new-session (:repository this) attrs))

  (renew-session
    [this sid]
    (let [session (get-session (:repository this) sid)]
      (mgmt/renew-session (:repository this) session)))

  (renew-sessions
    [this session-ch duration query]
    (mgmt/renew-sessions (:repository this) session-ch duration query))

  (session-timeout?
    [this sid]
    (mgmt/session-timeout? (:repository this) sid))

  (session-timeout?
    [this sid timeout]
    (mgmt/session-timeout? (:repository this) sid timeout))

  (session-valid?
    [this sid]
    (mgmt/session-valid? (:repository this) sid))

  (stop-mgmt-task
    [this session-ch stop-ch]
    (mgmt/stop-mgmt-task session-ch stop-ch))

  (terminate-session
    [this sid]
    (let [session (get-session (:repository this) this)]
      (mgmt/terminate-session (:repository this) session))))

(defn make-session-manager
  ([] (make-session-manager (make-session-repository)))
  ([repo] (->SessionManager repo)))
