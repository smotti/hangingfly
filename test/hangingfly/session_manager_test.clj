(ns hangingfly.session-manager-test
  (:import hangingfly.session_manager.SessionManager)
  (:require [clojure.test :refer :all]
            [hangingfly.session-manager :refer :all]))

(deftest test-make-session-manager
  (testing "with no argument"
    (let [mgr (make-session-manager)]
      (is (and (nil? (:session-store mgr))
               (empty? @(:session-coll mgr))))
      (is (instance? SessionManager mgr))))
  (testing "with argument for session-store"
    (let [mgr (make-session-manager 'session-store)]
      (is (and (= 'session-store (:session-store mgr))
               (empty? @(:session-coll mgr))))
      (is (instance? SessionManager mgr))))
  (testing "with arguments for session-store and session-coll"
    (let [session-coll {:hello 'world}
          mgr (make-session-manager 'session-store session-coll)]
      (is (and (= 'session-store (:session-store mgr))
               (= session-coll (:session-coll mgr))))
      (is (instance? SessionManager mgr)))))

(deftest test-new-session
  (let [mgr (->SessionManager nil (atom {}))
        session (new-session mgr)]
    (is (and (not (nil? session))
             (:valid? session)))
    (is (not-empty @(:session-coll mgr)))))

(deftest test-terminate-session
  (let [sid "SESSION-ID"
        session {:session-id sid
                 :start-time (System/currentTimeMillis)
                 :valid? true}
        mgr (->SessionManager nil (atom {sid session}))
        result (terminate-session mgr sid)]
    (is (empty? @(:session-coll mgr)))))

(deftest test-valid-session?
  (let [valid-sid "SESSION-ID"
        invalid-sid "INVALID-SESSION-ID"
        valid-session {:session-id valid-sid
                       :start-time (System/currentTimeMillis)
                       :valid? true}
        invalid-session {:session-id invalid-sid
                         :start-time (System/currentTimeMillis)
                         :valid? false}
        mgr (->SessionManager nil (atom {valid-sid valid-session
                                         invalid-sid invalid-session}))]
    (is (valid-session? mgr valid-sid))
    (is (not (valid-session? mgr invalid-sid)))))

(deftest test-invalidate-session
  (let [sid "SESSION-ID"
        session {:session-id sid
                 :start-time (System/currentTimeMillis)
                 :valid? true}
        mgr (->SessionManager nil (atom {sid session}))
        result (invalidate-session mgr sid)]
    (is (false? (:valid? result)))
    (is (not (nil? (:end-time result))))))

(deftest test-renew-session
  (let [old-sid "OLD-SESSION-ID"
        old-session {:session-id old-sid
                     :start-time (System/currentTimeMillis)
                     :valid? true}
        mgr (->SessionManager nil (atom {old-sid old-session}))
        new-session (renew-session mgr old-sid)]
    (is (not= old-sid (:session-id new-session)))
    (is (false? (get-in @(:session-coll mgr) [old-sid :valid?])))
    (is (= old-sid (:previous-session-id new-session)))
    (is (= 2 (count @(:session-coll mgr))))))
