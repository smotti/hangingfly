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
          salted-hash (generate-salted-hash (.getBytes sid StandardCharsets/UTF_8))]
      (is (string? salted-hash)))))

(deftest test-make-session
  (testing "with no arguments"
    (let [session (make-session)]
      (is (every? true?
                  (map #(not (nil? (% session)))
                       default-session-attrs))))))
