(ns hangingfly.session-manager
  (:require [hangingfly.session-management :as mgmt]
            [hangingfly.session-repository.atom :refer [make-session-repository]]))

(defprotocol ISessionManager
  (invalidate-sessions [this session-ch duration query])
  (new-session [this])
  (renew-session [this sid])
  (renew-sessions [this session-ch duration query])
  (session-timeout? [this sid] [this sid timeout])
  (session-valid? [this sid])
  (stop-mgmt-task [this session-ch stop-ch])
  (terminate-session [this sid]))

(defrecord SessionManager [repository]
  )

(defn make-session-manager
  ([] (make-session-manager (make-session-repository)))
  ([repo] (->SessionManager repo)))
