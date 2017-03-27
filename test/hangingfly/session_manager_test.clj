(ns hangingfly.session-manager-test
  (:import hangingfly.session_manager.SessionManager)
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :refer [sample]]
            [hangingfly.session-test-utils :refer :all]
            [hangingfly.session-repository.atom :refer [->SessionRepository]]
            [hangingfly.session-manager :refer :all]))

(deftest test-make-session-manager
  (let [sample-size 10
        sessions (sample random-session-gen sample-size)
        repo (->SessionRepository (atom (->map sessions)))
        result (make-session-manager repo)]
    (is (instance? SessionManager result))
    (is (= sample-size (count @(:database (:repository result)))))))
