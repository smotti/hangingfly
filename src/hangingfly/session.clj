(ns hangingfly.session
  (:import java.nio.charset.StandardCharsets
           (java.security MessageDigest SecureRandom)
           (java.util Base64)))

(def ^{:dynamic true} *default-timeouts*
  {:absolute (* 60 60)
   :idle (* 60 20)
   :renew (* 60 5)})

(def ^{:dynamic true} *default-prng* (SecureRandom/getInstance "SHA1PRNG"))

(def ^{:dynamic true} *default-digest-algorithm* "SHA-256")

(defrecord Session [session-id
                    start-time
                    end-time
                    salted-hash-sid
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
  (encode (.getBytes (.toString (.nextLong *default-prng*)))))

(defn- generate-salt
  "Generate a cryptographically secure salt of size n bytes. n defaults to 32."
  ([] (generate-salt 32))
  ([n] (.generateSeed *default-prng* n)))

(defn generate-salted-hash
  "Generates a base64 encoded salted-hash of the given bytes."
  [bs]
  (let [salt (generate-salt)
        md (MessageDigest/getInstance *default-digest-algorithm*)
        digest (.digest md (byte-array (concat (seq bs) (seq salt))))
        buffer (byte-array (+ (count digest) (count salt)))]
    (System/arraycopy digest 0 buffer 0 (count digest))
    (System/arraycopy salt 0 buffer (count digest) (count salt))
    (encode buffer)))

(defn make-session
  "Create a new session with default/custom attributes."
  ([]
   (make-session {}))
  ([attrs]
   (let [sid (generate-session-id)
         default-attrs {:start-time (System/currentTimeMillis)
                        :valid? true
                        :salted-hash-sid (generate-salted-hash
                                           (.getBytes sid StandardCharsets/UTF_8))}
         merged-attrs (assoc (merge default-attrs *default-timeouts* attrs)
                             :session-id
                             sid)]
     (map->Session merged-attrs))))
