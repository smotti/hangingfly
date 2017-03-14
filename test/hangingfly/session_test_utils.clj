(ns hangingfly.session-test-utils
  (:require [clojure.test.check.generators :as gen]))

(def time-offset-gen
  (gen/such-that #(not= 0 %)
                 (gen/large-integer* {:min 1000})))

(def session-start-time-gen
  (gen/fmap (fn [offset]
              (- (System/currentTimeMillis) offset))
            time-offset-gen))

(defn session-end-time-gen
  [start-time]
  (gen/such-that #(> % start-time) (gen/large-integer* {:min start-time})))

(def session-id-gen
  (gen/not-empty gen/string-alphanumeric))

(def session-valid?-gen
  gen/boolean)

(def session-timeout-gen
  (let [scale-to-min 10000]
    (gen/fmap (fn [v] (* v scale-to-min))
              gen/pos-int)))

(def session-gen
    (gen/fmap (fn [s]
                (let [start-time (:start-time s)
                      valid? (:valid? s)]
                  (assoc s
                         :end-time (when (not valid?)
                                     (gen/generate
                                       (session-end-time-gen start-time) 100)))))
              (gen/hash-map
                :session-id session-id-gen
                :start-time session-start-time-gen
                :valid? session-valid?-gen
                :absolute-timeout session-timeout-gen
                :idle-timeout session-timeout-gen
                :renewal-timeout session-timeout-gen)))

(defn gen-random-sessions
  ([] (gen-random-session 10))
  ([n] (gen/sample session-gen n)))

(defn ->map
  [sessions]
  (into {} (map #(vector (:session-id %) %) sessions)))
