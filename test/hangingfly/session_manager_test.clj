(ns hangingfly.session-manager-test
  (:import hangingfly.session_manager.SessionManager)
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :refer [fmap generate sample]]
            [hangingfly.session-manager :refer :all]
            [hangingfly.session-repository.atom :refer [->SessionRepository]]
            [hangingfly.session-test-utils :refer :all]))

(deftest test-make-session-manager
  (testing "with no argument"
    (let [mgr (make-session-manager)]
      (is (instance? clojure.lang.Atom (:database (:session-repo mgr))))
      (is (instance? SessionManager mgr))))

  (testing "with argument for session-store"
    (let [mgr (make-session-manager (->SessionRepository (atom {})))]
      (is (instance? clojure.lang.Atom (:database (:session-repo mgr))))
      (is (instance? SessionManager mgr)))))

(deftest test-new-session
  (let [repo (->SessionRepository (atom {}))
        mgr (->SessionManager repo)
        session (new-session mgr)]
    (is (and (not (nil? session))
             (:valid? session)))
    (is (not-empty @(:database (:session-repo mgr))))))

(deftest test-terminate-session
  (let [sid "SESSION-ID"
        session {:session-id sid
                 :start-time (System/currentTimeMillis)
                 :valid? true}
        repo (->SessionRepository (atom {sid session}))
        mgr (->SessionManager repo)
        result (terminate-session mgr sid)]
    (is (empty? @(:database (:session-repo mgr))))))

(deftest test-valid-session?
  (let [valid-sid "SESSION-ID"
        invalid-sid "INVALID-SESSION-ID"
        valid-session {:session-id valid-sid
                       :start-time (System/currentTimeMillis)
                       :valid? true}
        invalid-session {:session-id invalid-sid
                         :start-time (System/currentTimeMillis)
                         :valid? false}
        repo (->SessionRepository (atom {valid-sid valid-session
                                         invalid-sid invalid-session}))
        mgr (->SessionManager repo)]
    (is (true? (valid-session? mgr valid-sid)))
    (is (false? (valid-session? mgr "NO-SUCH-SESSION")))
    (is (false? (valid-session? mgr invalid-sid)))))

(deftest test-invalidate-session
  (testing "happy path"
    (let [sid "SESSION-ID"
          session {:session-id sid
                   :start-time (System/currentTimeMillis)
                   :valid? true}
          repo (->SessionRepository (atom {sid session}))
          mgr (->SessionManager repo)
          result (invalidate-session mgr sid)]
      (is (false? (:valid? result)))
      (is (not (nil? (:end-time result))))))

  (testing "exceptional paths"
    (let [sid "NO-SUCH-SESSION"
          mgr (->SessionManager (->SessionRepository (atom {})))]
      (is (nil? (invalidate-session mgr sid))))))

(deftest test-renew-session
  (testing "happy path"
    (let [old-sid "OLD-SESSION-ID"
          old-session {:session-id old-sid
                       :start-time (System/currentTimeMillis)
                       :valid? true}
          repo (->SessionRepository (atom {old-sid old-session}))
          mgr (->SessionManager repo)
          new-session (renew-session mgr old-sid)]
      (is (not= old-sid (:session-id new-session)))
      (is (false? (get-in @(:database (:session-repo mgr)) [old-sid :valid?])))
      (is (= old-sid (:previous-session-id new-session)))
      (is (= 2 (count @(:database (:session-repo mgr)))))))

  (testing "exceptional paths"
    (let [sid "NO-SUCH-SESSION"
          mgr (->SessionManager (->SessionRepository (atom {})))]
      (is (nil? (renew-session mgr sid))))))

(deftest test-session-timeout?
  (testing "for absolute-timeout"
    (let [absolute-timeout (* 60 60)
          scaled-timeout (* absolute-timeout 1000)
          attrs {:absolute-timeout absolute-timeout
                 :idle-timeout (* absolute-timeout 3)
                 :renewal-timeout (* absolute-timeout 3)
                 :start-time (- (System/currentTimeMillis)
                                (generate
                                  (time-offset-gen :min-offset scaled-timeout
                                                   :max-offset (* scaled-timeout 2))))}
          session (generate (fmap #(merge % attrs) valid-session-gen))
          sid (:session-id session)
          repo (->SessionRepository (atom {sid session}))
          mgr (->SessionManager repo)]
      (is (true? (session-timeout? mgr sid))))))

(defn start-time-gen
  [timeout]
  (- (System/currentTimeMillis)
     (generate (time-offset-gen :min-offset timeout
                                :max-offset (* timeout 2)))))

(deftest test-invalidate-sessions
  (testing "happy path"
    (testing "absolute timeout"
      (let [absolute-timeout (* 60 60)
            scaled-timeout (* absolute-timeout 1000)
            sessions (sample
                       (fmap #(assoc %
                                     :start-time (start-time-gen scaled-timeout)
                                     :absolute-timeout absolute-timeout
                                     :idle-timeout (* absolute-timeout 3)
                                     :renewal-timeout (* absolute-timeout 2))
                             valid-session-gen))
            repo (->SessionRepository (atom (->map sessions)))
            mgr (->SessionManager repo)
            result (invalidate-sessions mgr)]
        (is (and (not-empty result)
                 (every? #(false? (:valid? %)) result)))
        (is (every? #(false? (:valid? (second %))) @(:database repo)))))

    (testing "idle timeout"
      (let [idle-timeout (* 60 20)
            scaled-timeout (* idle-timeout 1000)
            sessions (sample
                       (fmap #(assoc %
                                     :start-time (start-time-gen scaled-timeout)
                                     :absolute-timeout (* idle-timeout 3)
                                     :idle-timeout idle-timeout
                                     :renewal-timeout (* idle-timeout 3))
                             valid-session-gen))
            repo (->SessionRepository (atom (->map sessions)))
            mgr (->SessionManager repo)
            result (invalidate-sessions mgr)]
        (is (and (not-empty result)
                 (every? #(false? (:valid? %)) result)))
        (is (every? #(false? (:valid? (second %))) @(:database repo)))))

    (testing "renewal timeout"
      (let [renewal-timeout (* 60 5)
            scaled-timeout (* renewal-timeout 1000)
            sessions (sample
                       (fmap #(assoc %
                                     :start-time (start-time-gen scaled-timeout)
                                     :absolute-timeout (* renewal-timeout 3)
                                     :idle-timeout (* renewal-timeout 3)
                                     :renewal-timeout renewal-timeout)
                             valid-session-gen))
            repo (->SessionRepository (atom (->map sessions)))
            mgr (->SessionManager repo)
            result (invalidate-sessions mgr)]
        (is (and (not-empty result)
                 (every? #(false? (:valid? %)) result)))
        (is (every? #(false? (:valid? (second %))) @(:database repo)))))))
