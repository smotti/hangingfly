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
  [start-time & {:keys [absolute-timeout idle-timeout renewal-timeout]}]
  (let [pred #(not (nil? %))
        timeout (cond
                  (pred absolute-timeout) absolute-timeout
                  (pred idle-timeout) idle-timeout
                  (pred renewal-timeout) renewal-timeout
                  :else 0)
        with-timeout (+ start-time (* timeout 1000))]
    (gen/such-that #(> % with-timeout)
                   (gen/large-integer* {:min with-timeout}))))

(def session-id-gen
  (gen/not-empty gen/string-alphanumeric))

(def session-valid?-gen
  gen/boolean)

(def session-timeout-gen
  (let [scale-to-min 10000]
    (gen/fmap (fn [v] (* v scale-to-min))
              gen/pos-int)))

(def session-gen
  (gen/hash-map :session-id session-id-gen
                :start-time session-start-time-gen
                :absolute-timeout session-timeout-gen
                :idle-timeout session-timeout-gen
                :renewal-timeout session-timeout-gen))

(def valid-session-gen
  (gen/fmap #(assoc % :end-time nil :valid? true) session-gen))

(def invalid-session-gen
   (gen/fmap #(let [start-time (:start-time %)]
                (assoc %
                       :end-time (gen/generate
                                   (session-end-time-gen start-time) 100)
                       :valid? false))
             session-gen))

(def random-session-gen
  (gen/one-of [valid-session-gen invalid-session-gen]))

(defn ->map
  [sessions]
  (into {} (map #(vector (:session-id %) %) sessions)))

(defn session-with-attrs-gen
  [attrs] 
  (gen/fmap (fn [s]
              (merge s attrs))
            random-session-gen))

(defn timeout-session-gen
  [timeout value] 
  (gen/fmap #(let [start-time (:start-time %)]
                (assoc % timeout value
                         :end-time (gen/generate
                                     (session-end-time-gen start-time
                                                           timeout
                                                           value))))
            session-gen))

(defn sample-timeout-session-gen
  [timeout value n]
  (gen/sample (timeout-session-gen timeout value) n))
