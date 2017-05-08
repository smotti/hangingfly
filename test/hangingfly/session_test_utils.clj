(ns hangingfly.session-test-utils
  (:require [clojure.test.check.generators :as gen]))

;; Custom keyword-gen because gen/keyword generates keywords
;; that are too long.
(def keyword-gen
  (gen/fmap keyword
            (gen/not-empty (gen/such-that #(<= (count %) 16)
                                          gen/string-alphanumeric))))

(defn time-offset-gen
  [& {:keys [min-offset max-offset] :or {min-offset 1000 max-offset 30000}}]
  (gen/such-that #(not= 0 %)
                 (gen/large-integer* {:min min-offset :max max-offset})))

(def time-gen
  (gen/fmap #(- (System/currentTimeMillis) %) (time-offset-gen)))

(def date-gen
  (gen/fmap #(java.util.Date. %) time-gen))

(def session-attribute-value-gen
  (gen/one-of [gen/boolean gen/int gen/string-alphanumeric time-gen date-gen]))

(def session-attribute-gen
  (gen/map keyword-gen
           session-attribute-value-gen
           {:min-elements 1 :max-elements 10}))

(defn start-time-gen
  [timeout]
  (- (System/currentTimeMillis)
     (gen/generate (time-offset-gen :min-offset timeout
                                    :max-offset (* timeout 2)))))
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
                :salted-id session-id-gen
                :start-time session-start-time-gen
                :absolute-timeout session-timeout-gen
                :idle-timeout session-timeout-gen
                :renewal-timeout session-timeout-gen
                :session-attributes session-attribute-gen
                :previous-session-ids (gen/vector-distinct session-id-gen
                                                           {:min-elements 0
                                                            :max-elements 10})))

(def valid-session-gen
  (gen/fmap #(assoc %
                    :end-time nil
                    :is-valid true
                    :cause-for-termination nil) session-gen))

(def invalid-session-gen
   (gen/fmap #(let [start-time (:start-time %)]
                (assoc %
                       :end-time (gen/generate
                                   (session-end-time-gen start-time) 100)
                       :is-valid false))
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

(defn absolute-timeout-session-gen
  [timeout]
  (let [scaled-timeout (* timeout 1000)]
    (gen/fmap #(assoc % :start-time (start-time-gen scaled-timeout)
                        :absolute-timeout timeout
                        :idle-timeout (* timeout 3)
                        :renewal-timeout (* timeout 3))
              valid-session-gen)))

(defn idle-timeout-session-gen
  [timeout]
  (let [scaled-timeout (* timeout 1000)]
    (gen/fmap #(assoc % :start-time (start-time-gen scaled-timeout)
                        :absolute-timeout (* timeout 3)
                        :idle-timeout timeout
                        :renewal-timeout (* timeout 3))
              valid-session-gen)))

(defn renewal-timeout-session-gen
  [timeout]
  (let [scaled-timeout (* timeout 1000)]
    (gen/fmap #(assoc % :start-time (start-time-gen scaled-timeout)
                        :absolute-timeout (* timeout 3)
                        :idle-timeout (* timeout 3)
                        :renewal-timeout timeout)
              valid-session-gen)))
