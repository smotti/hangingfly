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
    (is (true? (valid-session? mgr valid-sid)))
    (is (false? (valid-session? mgr "NO-SUCH-SESSION")))
    (is (false? (valid-session? mgr invalid-sid)))))

(deftest test-invalidate-session
  (testing "happy path"
    (let [sid "SESSION-ID"
          session {:session-id sid
                   :start-time (System/currentTimeMillis)
                   :valid? true}
          mgr (->SessionManager nil (atom {sid session}))
          result (invalidate-session mgr sid)]
      (is (false? (:valid? result)))
      (is (not (nil? (:end-time result))))))

  (testing "exceptional paths"
    (let [sid "NO-SUCH-SESSION"
          mgr (->SessionManager nil (atom {}))]
      (is (nil? (invalidate-session mgr sid))))))

(deftest test-renew-session
  (testing "happy path"
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

  (testing "exceptional paths"
    (let [sid "NO-SUCH-SESSION"
          mgr (->SessionManager nil (atom {}))]
      (is (nil? (renew-session mgr sid))))))

(deftest test-invalidate-session
  (testing "happy path"
    (testing "absolute timeout"
      (let [sid "SESSION-ID"
            absolute-timeout (* 60 60)
            session {:session-id sid
                     :absolute-timeout absolute-timeout
                     :start-time (- (System/currentTimeMillis)
                                    (* absolute-timeout 1000)
                                    (* 5 1000) ; Additional 5secs just in case
                                    )}
            mgr (->SessionManager nil (atom {sid session}))]
        (is (not (nil? (invalidate-sessions mgr))))

        (let [invalidated-session (get @(:session-coll mgr) sid)]
          (is (false? (:valid? invalidated-session)))
          (is (not (nil? (:end-time invalidated-session)))))))

    (testing "idle timeout"
      (let [sid "SESSION-ID"
            idle-timeout (* 60 20)
            session {:session-id sid
                     :idle-timeout idle-timeout
                     :start-time (- (System/currentTimeMillis)
                                    (* idle-timeout 1000)
                                    (* 5 1000))}
            mgr (->SessionManager nil (atom {sid session}))]
        (is (not (nil? (invalidate-sessions mgr))))
        
        (let [invalidated-session (get @(:session-coll mgr) sid)]
          (is (false? (:valid? invalidated-session)))
          (is (not (nil? (:end-time invalidated-session)))))))

    (testing "renewal timeout"
      (let [sid "SESSION-ID"
            renewal-timeout (* 60 5)
            session {:session-id sid
                     :renewal-timeout renewal-timeout
                     :start-time (- (System/currentTimeMillis)
                                    (* renewal-timeout 1000)
                                    (* 5 1000))}
            mgr (->SessionManager nil (atom {sid session}))]
        (is (not (nil? (invalidate-sessions mgr))))

        (let [invalidated-session (get @(:session-coll mgr) sid)
              new-session (into {} [(last @(:session-coll mgr))])]
          (is (false? (:valid? invalidated-session)))
          (is (not (nil? (:end-time invalidated-session))))
          (is (not= sid (:session-id new-session)))
          (is (= sid (:previous-session-id new-session))))))))
