(ns hangingfly.session-repository.atom
  (:require [hangingfly.session-repository.core :refer :all]))

(defrecord SessionRepository [database]
  ISessionRepository

  (get-session
    [this sid]
    (get @(:database this) sid))
  (get-all-sessions
    [this]
    (into [] (vals @(:database this))))
  (add-session
    [this session]
    (swap! (:database this) assoc (:session-id session) session)
    session)
  (remove-session
    [this sid]
    (let [session (get-session this sid)]
      (swap! (:database this) dissoc sid)
      session))
  (update-session
    [this updated-session]
    (let [sid (:session-id updated-session)]
      (when-let [old-session (get-session this sid)]
        (add-session this updated-session))))
  (execute-query
    [this query]
    (let [sessions @(:database this)]
      (apply query [(into [] (vals sessions))])))
  )

(defn make-session-repository
  ([] (make-session-repository (atom {})))
  ([database] (->SessionRepository database)))
