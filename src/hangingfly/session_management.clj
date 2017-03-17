(ns hangingfly.session-management
  (:require [hangingfly.session :refer [make-session renew terminate timeout?
                                        valid?]]
            [hangingfly.session-repository.core :refer [add-session
                                                        get-session
                                                        update-session]]))

(defn new-session
  ([repo]
   (let [session (make-session)]
     (add-session repo session)
     session))
  ([repo attrs]
   (let [session (make-session attrs)]
     (add-session repo session)
     session)))

(defn renew-session
  [repo session]
  (let [{old-session :old new-session :new :as sessions} (renew session)]
    (update-session repo old-session)
    (add-session repo new-session)
    sessions))

(defn session-timeout?
  ([repo sid]
   (if-let [session (get-session repo sid)]
     (timeout? session)
     false))
  ([repo sid timeout]
   (if-let [session (get-session repo sid)]
     (timeout? session timeout)
     false)))

(defn session-valid?
  [repo sid]
  (if-let [session (get-session repo sid)]
    (valid? session)
    false))

(defn terminate-session
  [repo session]
  (let [terminated-session (terminate session)]
    (update-session repo terminated-session)
    terminated-session))
