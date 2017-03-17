(ns hangingfly.session-test
  (:import java.nio.charset.StandardCharsets)
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :refer [generate sample]]
            [hangingfly.session :refer :all]
            [hangingfly.session-test-utils :refer :all]))

(def default-session-attrs [:absolute :idle :renew :start-time :valid? :salted-hash-sid])

(deftest test-generate-session-id
  (let [sid (generate-session-id)
        length (count sid)]
    (is (or (= 24 length)
            (= 28 length)))
    (is (string? sid))))

(deftest test-generate-salted-hash
  (testing "generate salted-hash of a session id"
    (let [sid (generate-session-id)
          sid-bytes (.getBytes sid StandardCharsets/UTF_8)
          salted-hash-1 (generate-salted-hash sid-bytes)
          salted-hash-2 (generate-salted-hash sid-bytes)]
      (is (and (string? salted-hash-1) (string? salted-hash-2)))
      (is (not= salted-hash-1 salted-hash-2)))))

(deftest test-make-session
  (testing "with no arguments"
    (let [session (with-redefs-fn {#'hangingfly.session/generate-salted-hash
                                   (fn [_] "TIHIHI")}
                    #(make-session))]
      (is (every? true?
                  (map #(not (nil? (% session)))
                       default-session-attrs)))))
  (testing "with attributes as argument"
    (let[start-time (System/currentTimeMillis)
          attrs {:start-time start-time}
          session (with-redefs-fn {#'hangingfly.session/generate-salted-hash
                                   (fn [_] "TIHIHI")}
                    #(make-session attrs))]
      (is (= start-time (:start-time session))))))

(deftest test-renew
  (let [session (generate valid-session-gen)
        sid (:session-id session)
        result (with-redefs-fn {#'hangingfly.session/generate-salted-hash
                                (fn [_] "TIHIHI")}
                 #(renew session))]
    (is (= '(:old :new) (keys result)))
    (is (= (:next-session-id (:old result))
           (:session-id (:new result))))
    (is (not (nil? (:end-time (:old result)))))
    (is (= sid (:previous-session-id (:new result))))))

(deftest test-terminate
  (let [session (generate valid-session-gen)
        result (terminate session)]
    (is (and (false? (:valid? result))
             (not (nil? (:end-time result)))))))

(deftest test-timeout
  (let [absolute (sample (absolute-timeout-session-gen (* 60 60)))
        idle (sample (idle-timeout-session-gen (* 60 20)))
        renewal (sample (renewal-timeout-session-gen (* 60 5)))]
    (testing "check if any timeout was reached"
      (is (every? timeout? absolute))
      (is (every? timeout? idle))
      (is (every? timeout? renewal)))
    (testing "by specifying for which timeout to check"
      (is (every? #(timeout? % :absolute-timeout) absolute))
      (is (every? #(timeout? % :idle-timeout) idle))
      (is (every? #(timeout? % :renewal-timeout) renewal)))))

(deftest test-valid?
  (let [valid-session (generate valid-session-gen)
        invalid-session (generate invalid-session-gen)]
    (is (valid? valid-session))
    (is (not (valid? invalid-session)))))
