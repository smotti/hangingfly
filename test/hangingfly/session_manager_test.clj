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

(deftest test-add-session
  (let [mgr (->SessionManager nil (atom {}))
        session {:start-time (System/currentTimeMillis)
                 :valid? true
                 :session-id "SESSION-ID"}
        result (#'hangingfly.session-manager/add-session mgr session)]
    (is (not (empty? result)))
    (is (= session
           (get @(:session-coll mgr) (:session-id session))))))

(deftest test-new-session
  (let [mgr (->SessionManager nil (atom {}))
        session (new-session mgr)]
    (is (and (not (nil? session))
             (:valid? session)))))
