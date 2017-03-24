(ns hangingfly.session-management
  (:require [clojure.core.async :refer [>! >!! <!! alts!! chan close! go-loop
                                        thread timeout]]
            [hangingfly.session :refer [make-session renew terminate timeout?
                                        valid?]]
            [hangingfly.session-repository.core :refer [add-session
                                                        find-sessions
                                                        get-session
                                                        update-session]]))

(declare terminate-session)

(defn invalidate-sessions
  "Invalidates (terminates) sessions found by query every duration seconds.
  Returns a vector of two elements:
  1. element: A channel that returns the result of the thread when done
  2. element: A channel to send the :stop msg to the thread

  This fn is spawning a long running thread that fetches and invalidates
  the sessions. The invalidated sessions are than send to the caller by means
  of smaller CSPs via the session-chan. When the session-chan gets closed the
  CSPs stop processing and the thread might terminate too, but to ensure that
  everything gets cleaned up properly :stop has to be send via the returned
  close-channel. After the thread terminated :stopped is put onto the channel,
  that is the first element of the vector this fn returns.

  Note that you can use stop-invalidate-sessions which takes care of stopping
  this process.

  Note it is better to provide a buffered session-chan to avoid the loss of
  invalidated sessions when closing the channel."
  [repo session-chan duration query]
  (let [stop-chan (chan 1)]
    [(thread
         (loop [stop? false]
           (if (= stop? :stop)
             (do
               (close! stop-chan)
               :stopped)
             (do
               (<!! (timeout (* duration 1000)))
               (let [sessions (find-sessions repo query)
                     invalidated-sessions (doall
                                            (map #(terminate-session repo %)
                                                 sessions))]
                 (if (empty? invalidated-sessions)
                   (recur (first (alts!! [stop-chan (timeout 50)])))
                   (do
                     (go-loop [is invalidated-sessions]
                        (when-let [s (first is)]
                          (when (>! session-chan s)
                            (recur (rest is)))))
                     (recur (first (alts!! [stop-chan (timeout 50)]))))))))))
     stop-chan]))

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

(defn stop-invalidate-sessions
  [session-chan stop-chan]
  (>!! stop-chan :stop)
  (close! session-chan))

(defn terminate-session
  [repo session]
  (let [terminated-session (terminate session)]
    (update-session repo terminated-session)
    terminated-session))
