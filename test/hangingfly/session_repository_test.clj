(ns hangingfly.session-repository-test
  (:import (hangingfly.session_repository.atom SessionRepository))
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :refer [sample]]
            [hangingfly.session-repository.core :refer :all]
            [hangingfly.session-repository.atom :refer :all]
            [hangingfly.session-test-utils :refer :all]))

(deftest test-make-session-repository
  (let [repo (make-session-repository)]
    (is (instance? SessionRepository repo))
    (is (extends? ISessionRepository (type repo)))
    (is (instance? clojure.lang.Atom (:database repo)))))

(deftest test-get-session
  (let [sid "SESSION-ID"
        session {:session-id sid
                 :start-time (System/currentTimeMillis)} 
        repo (->SessionRepository (atom {sid session}))]
    (testing "the happy path"
      (let [result (get-session repo sid)]
        (is (= sid (:session-id result)))))
    (testing "the unhappy path"
      (let [result (get-session repo "NO-SUCH-SESSION")]
        (is (nil? result))))))

(deftest test-get-sessions
  (let [sessions (sample random-session-gen)
        repo (->SessionRepository (atom (->map sessions)))
        result (get-sessions repo)]
    (is (= (count sessions) (count result)))
    (is (= (sort (map :session-id sessions))
           (sort (map :session-id result))))))

(deftest test-add-session
  (let [sid "SESSION-ID"
        session {:session-id sid
                 :start-time (System/currentTimeMillis)}
        repo (->SessionRepository (atom {}))]
    (testing "the happy path"
      (let [result (add-session repo session)]
        (is (not (nil? (get @(:database repo) sid))))
        (is (= 1 (count result)))))))

(deftest test-remove-session
  (let [sid "SESSION-ID"
        session {:session-id sid
                 :start-time (System/currentTimeMillis)}
        repo (->SessionRepository (atom {sid session}))]
    (testing "the happy path"
      (let [result (remove-session repo sid)]
        (is (= sid (:session-id result)))
        (is (= 0 (count @(:database repo))))))
    (testing "the unhappy path"
      (let [result (remove-session repo sid)]
        (is (nil? result))))))

(deftest test-update-session
  (let [sid "SESSION-ID"
        session {:session-id sid
                 :start-time (System/currentTimeMillis)
                 :an-attribute 'hello}
        updated-session {:session-id sid
                         :an-attribute 'world}
        repo (->SessionRepository (atom {sid session}))]
    (testing "the happy path"
      (update-session repo updated-session)
      (let [repo-session (get @(:database repo) sid)]
        (is (= (:session-id updated-session) (:session-id repo-session)))
        (is (= (:an-attribute updated-session) (:an-attribute repo-session)))))
    (testing "the unhappy path"
      (swap! (:database repo) dissoc sid)
      (let [result (update-session repo updated-session)]
        (is (nil? result))))))

(deftest test-find-sessions
  (let [query (fn [sessions]
                (filter #(true? (:valid? %)) sessions))
        sessions (sample random-session-gen 60)
        repo (->SessionRepository (atom (->map sessions)))
        result (find-sessions repo query)]
    (is (every? #(true? (:valid? %)) result))))
