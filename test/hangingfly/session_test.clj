(ns hangingfly.session-test
  (:import java.nio.charset.StandardCharsets)
  (:require [clojure.test :refer :all]
            [hangingfly.session :refer :all]))

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
    (let [session (make-session)]
      (is (every? true?
                  (map #(not (nil? (% session)))
                       default-session-attrs)))))
  (testing "with attributes as argument"
    (let[start-time (System/currentTimeMillis)
          attrs {:start-time start-time}
          session (make-session attrs)]
      (is (= start-time (:start-time session))))))
