(ns hangingfly.session-management-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :refer [generate sample]]
            [clojure.core.async :refer [>!! <!! alts!! chan close! timeout]]
            [hangingfly.session-management :refer :all]
            [hangingfly.session-repository.atom :refer [->SessionRepository]]
            [hangingfly.session :refer [timeout?]]
            [hangingfly.session-test-utils :refer :all]))

(deftest test-invalidate-sessions
  (let [duration-sec 1
        sample-size 10
        absolute (sample (absolute-timeout-session-gen (* 60 60)) sample-size)
        query (fn [sessions] (filter #(if (or (timeout? % :absolute-timeout)
                                              (timeout? % :idle-timeout))
                                        true
                                        false)
                                     sessions))
        repo (->SessionRepository (atom (->map absolute)))
        session-chan (chan)
        [t-ch stop-ch] (invalidate-sessions repo session-chan duration-sec query)]

    (<!! (timeout 1000)) ; Need to wait until the duration-sec is over

    (let [result (loop [sessions '()
                        msg (alts!! [session-chan (timeout 100)])]
                   (let [v (first msg)]
                     (if (nil? v)
                       sessions
                       (recur (conj sessions v)
                              (alts!! [session-chan (timeout 100)])))))]
      (>!! stop-ch :stop)
      (close! session-chan)
      (is (= sample-size (count result)))
      (is (every? #(not (:valid? %)) result))
      (is (every? #(not (nil? (:end-time %))) result))
      (is (= :stopped (<!! t-ch))))))

(deftest test-new-session
  (testing "with no additional attributes"
    (let [hashed-sid "TIHIHI"
          repo (->SessionRepository (atom {}))
          result (with-redefs-fn {#'hangingfly.session/generate-salted-hash
                                  (fn [_] hashed-sid)}
                   #(new-session repo))]
      (is (:valid? result))
      (is (= hashed-sid (:salted-hash-sid result)))
      (is (= 1 (count @(:database repo))))
      (is (get @(:database repo) (:session-id result)))))
  (testing "with additional attributes"
    (let [hashed-sid "TIHIHI"
          attrs {:attr1 'hello :attr2 'world}
          repo (->SessionRepository (atom {}))
          result (with-redefs-fn {#'hangingfly.session/generate-salted-hash
                                  (fn [_] hashed-sid)}
                   #(new-session repo attrs))]
      (is (:valid? result))
      (is (= hashed-sid (:salted-hash-sid result)))
      (is (= 1 (count @(:database repo))))
      (is (get @(:database repo) (:session-id result))))))

(deftest test-renew-session
  (let [hashed-sid "TIHIHI"
        session (generate valid-session-gen)
        sid (:session-id session)
        repo (->SessionRepository (atom {sid session}))
        result (with-redefs-fn {#'hangingfly.session/generate-salted-hash
                                (fn [_] hashed-sid)}
                 #(renew-session repo sid))]
    (is (= '(:old :new) (keys result)))
    (is (= (:session-id (:old result)) (:previous-session-id (:new result))))
    (is (= (:next-session-id (:old result) (:session-id (:new result)))))
    (is (= 2 (count @(:database repo))))
    (is (= (:old result) (get @(:database repo) sid)))
    (is (= (:new result) (get @(:database repo) (:session-id (:new result)))))))

(deftest test-renew-sessions
  (let [duration-sec 1
        sample-size 10
        renewal (sample (renewal-timeout-session-gen (* 60 5)) sample-size)
        query (fn [sessions] (filter #(if (timeout? % :renewal-timeout)
                                        true
                                        false)
                                     sessions))
        repo (->SessionRepository (atom (->map renewal)))
        session-chan (chan)
        [t-ch stop-ch] (renew-sessions repo session-chan duration-sec query)]
    
    (<!! (timeout 1000)) ; Need to wait until the duration-sec is over

    (let [result (loop [sessions '()
                        msg (alts!! [session-chan (timeout 100)])]
                   (let [v (first msg)]
                     (if (nil? v)
                       sessions
                       (recur (conj sessions v)
                              (alts!! [session-chan (timeout 100)])))))]
      (>!! stop-ch :stop)
      (close! session-chan)
      (is (= sample-size (count result)))
      (is (every? #(:valid? (:new %)) result))
      (is (every? #(contains? (:new %) :previous-session-id) result))
      (is (every? #(nil? (:end-time (:new %))) result))
      (is (= :stopped (<!! t-ch))))))

(deftest test-session-timeout?
  (let [absolute (generate (absolute-timeout-session-gen (* 60 60)))
        idle (generate (idle-timeout-session-gen (* 60 20)))
        renewal (generate (renewal-timeout-session-gen (* 60 5)))
        sessions [absolute idle renewal]
        repo (->SessionRepository (atom (->map sessions)))]
    (is (every? #(session-timeout? repo (:session-id %)) sessions))
    (is (true? (session-timeout? repo (:session-id absolute) :absolute-timeout)))
    (is (true? (session-timeout? repo (:session-id idle) :idle-timeout)))
    (is (true? (session-timeout? repo (:session-id renewal) :renewal-timeout)))))

(deftest test-session-valid?
  (let [invalid-session (generate invalid-session-gen)
        valid-session (generate valid-session-gen)
        sessions [invalid-session valid-session]
        repo (->SessionRepository (atom (->map sessions)))]
    (is (not (session-valid? repo (:session-id invalid-session))))
    (is (session-valid? repo (:session-id valid-session)))))

(deftest test-terminate-session
  (let [session (generate valid-session-gen)
        sid (:session-id session)
        repo (->SessionRepository (atom {sid session}))
        result (terminate-session repo sid)]
    (is (false? (:valid? result)))
    (is (not (nil? (:end-time result))))
    (is (empty @(:database repo)))))
