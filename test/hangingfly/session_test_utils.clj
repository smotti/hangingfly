(ns hangingfly.session-test-utils
  (:require [clojure.test.check.generators :as gen]))

(defn time-offset-gen
  [& {:keys [min-offset max-offset] :or {min-offset 1000 max-offset 30000}}]
  (gen/such-that #(not= 0 %)
                 (gen/large-integer* {:min min-offset :max max-offset})))

(def session-start-time-gen
  (gen/fmap (fn [offset]
              (- (System/currentTimeMillis) offset))
            (time-offset-gen)))

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
  gen/pos-int)

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
