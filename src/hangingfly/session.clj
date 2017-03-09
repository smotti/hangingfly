(ns hangingfly.session
  (:import java.nio.charset.StandardCharsets
           (java.security MessageDigest SecureRandom)
           (java.util Base64)))

(def ^{:private true} default-timeouts {:absolute (* 60 60)
                                        :idle (* 60 20)
                                        :renew (* 60 5)})

(defrecord Session [session-id
                    start-time
                    end-time
                    salted-has-sid
                    valid?
                    prev-sid
                    cause-for-termination])

(defn- encode
  "Encodes the given bytes as a base64 string."
  [to-encode]
  (.encodeToString (Base64/getEncoder) to-encode))

(defn generate-session-id
  "Generates a random number (of type Long) using SHA1PRNG algorithm and
  encodes it as base64 string. Note that nextLong() is inherited from
  java.util.Random and uses only 48 bits for its seed. This means we can't get
  all possible Long values."
  []
  (let [prng (SecureRandom/getInstance "SHA1PRNG")]
    (encode (.getBytes (.toString (.nextLong prng))))))

(defn generate-salted-hash
  "Generates a base64 encoded salted-hash of the given bytes."
  [bs]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (encode (.digest digest bs))))

(defn make-session
  "Create a new session with default/custom attributes."
  ([]
   (make-session (generate-session-id)))
  ([sid]
   (make-session sid {:start-time (System/currentTimeMillis)
                      :valid? true
                      :salted-hash-sid (generate-salted-hash
                                         (.getBytes sid StandardCharsets/UTF_8))}))
  ([sid attrs]
   (map->Session (assoc (merge default-timeouts attrs) :session-id sid))))
