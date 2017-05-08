(ns hangingfly.session-repository-rdbms-test
  (:import java.io.File)
  (:require [hangingfly.session-repository.core :refer :all]
            [hangingfly.session-repository.rdbms :as sut]
            [hangingfly.session-test-data :refer [absolute-sessions]]
            [hangingfly.session-test-utils :refer [valid-session-gen]]
            [clojure.test :as t]
            [clojure.test.check.generators :refer [generate]]
            [clojure.java.io :refer [resource]]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.shell :refer [sh]]
            [clojure.string :refer [join]]
            [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [honeysql.core :as sql]
            [honeysql.helpers :as sqlh]))

(declare keys->snake_case)

(def ^:dynamic *DB* nil)

(def SQLITE-CREATE-TABLES
  [(.getFile (resource "database/sqlite/hangingfly.up.sql"))])

(defn create-tmp-db
  []
  (let [tmp-db-file (File/createTempFile "test_" ".sqlite")]
    (doseq [script SQLITE-CREATE-TABLES]
      (sh "/bin/sh"
          "-c"
          (str "sqlite3 "
               (.getAbsolutePath tmp-db-file)
               " < "
               script)))
    tmp-db-file))

(defn bool->int
  [x]
  (condp = x
    false 0
    true 1))

(defn insert-test-sessions
  [db]
  (let [session-xform (comp keys->snake_case
                            #(assoc %
                                    :is-valid (bool->int (:is-valid %))
                                    :previous-session-ids (join ","
                                                                (:previous-session-ids %)))
                            #(into {} %))
        sessions (mapv session-xform absolute-sessions)
        query (-> (sqlh/insert-into :session)
                  (sqlh/values sessions)
                  sql/format)]
    (jdbc/db-do-prepared db true query)))

(def keys->snake_case
  (fn [m] (reduce-kv #(assoc %1 (->snake_case %2) %3) {} m)))

(defn with-sqlite
  [f]
  (let [db-file (create-tmp-db)
        db-spec {:connection-uri (str "jdbc:sqlite:"
                                      (.getAbsolutePath db-file))}]
    (alter-var-root #'*DB* (constantly db-spec))
    (jdbc/with-db-connection [conn db-spec]
      (insert-test-sessions conn))
    (f)
    (.delete db-file)))

(t/use-fixtures :each with-sqlite)

;;;;;;;;;;
;;; TESTS
;;;;;;;;;;

(t/deftest test-delete
  (let [repo (sut/->SessionRepository *DB*)]
    (t/testing "delete a existing session"
      (let [session-id "n68"
            result (delete repo session-id)]
        (t/is (= 1 result))))
    (t/testing "delete a non-existing session"
      (let [session-id "NOPE"
            result (delete repo session-id)]
        (t/is (= 0 result))))))

(t/deftest test-get-one
  (t/testing "session with specified id exists"
    (let [repo (sut/->SessionRepository *DB*)
          session-id "n68"
          session (first absolute-sessions)
          result (get-one repo session-id)]
      (t/is (= session (dissoc result :session-attributes)))))
  (t/testing "session with specified id doesn't exist"
    (let [repo (sut/->SessionRepository *DB*)
          session-id "NOPE"
          result (get-one repo session-id)]
      (t/is (nil? result)))))

(t/deftest test-get-many
  (let [repo (sut/->SessionRepository *DB*)
        total (count absolute-sessions)]
    (t/testing "get all sessions"
      (t/is (= total (count (get-many repo)))))
    (t/testing "get a limited amount of sessions"
      (let [limit 5
            result (get-many repo :limit limit)]
        (t/is (= limit (count result)))))
    (t/testing "get all sessions starting from an offset"
      (let [offset 5
            amount (- total offset)
            result (get-many repo :offset offset)]
        (t/is (= amount (count result)))))
    (t/testing "get a limited amount of sessions starting from an offset"
      (let [limit 2
            offset 5
            result (get-many repo :limit limit :offset offset)]
        (t/is (= limit (count result)))))))

(t/deftest test-find
  (let [repo (sut/->SessionRepository *DB*)]
    (t/testing "find session by id"
      (let [session-id "n68"
            session (first absolute-sessions)
            query (-> (sqlh/select :*)
                      (sqlh/where [:= :session-id session-id]))
            result (first (find repo query))]
        (t/is (= session result))))
    (t/testing "find all valid sessions"
      (let [sessions (filter #(true? (:is-valid %)) absolute-sessions)
            query (-> (sqlh/select :*)
                      (sqlh/where [:= :is-valid 1]))
            result (find repo query)]
        (t/is (= (count sessions) (count result)))
        (t/is (= sessions result))))
    (t/testing "find all sessions that are a child session")
    (t/testing "find all sessions terminated because of idleing")
    (t/testing "find all sessions terminated due to absolute timeout")))

(t/deftest test-save
  (let [repo (sut/->SessionRepository *DB*)]
    (t/testing "Save new session without any additional attributes"
      (let [session (dissoc (generate valid-session-gen) :session-attributes)
            save-result (save repo session)
            qry-result (get-one repo (:session-id session))]
        (t/is (= 1 save-result))
        (t/is (= session (dissoc qry-result :session-attributes)))))
    (t/testing "Save a new session with additional attributes"
      (let [session (generate valid-session-gen)
            save-result (save repo session)
            qry-result (get-one repo (:session-id session))]
        (t/is (= 1 save-result))
        (t/is (= session qry-result))))
    (t/testing "Update an existing session"
      (let [session (generate valid-session-gen)
            save-result (save repo session)
            session (assoc session :is-valid false)
            update-result (save repo session)
            result (get-one repo (:session-id session))]
        (t/is (= (:is-valid session) (:is-valid result)))
        (t/is (= session result))))))
