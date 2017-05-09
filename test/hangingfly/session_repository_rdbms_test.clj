(ns hangingfly.session-repository-rdbms-test
  (:import java.io.File)
  (:require [hangingfly.session-repository.core :refer :all]
            [hangingfly.session-repository.rdbms :as sut]
            [hangingfly.session-test-utils :refer [valid-session-gen]]
            [clojure.test :as t]
            [clojure.test.check.generators :refer [generate sample]]
            [clojure.java.io :refer [resource]]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.shell :refer [sh]]
            [clojure.string :refer [join]]
            [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [honeysql.core :as sql]
            [honeysql.helpers :as sqlh]))

(def ^:dynamic *DB* nil)

(def ^:dynamic *SESSIONS* nil)

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
  (let [test-sessions (sample valid-session-gen)]
    (jdbc/with-db-transaction [conn db]
      (doseq [s test-sessions]
        (#'hangingfly.session-repository.rdbms/add-session {:conn conn} s)))
    (alter-var-root #'*SESSIONS* (constantly test-sessions))))

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
          session (first *SESSIONS*)
          session-id (:session-id session)
          result (get-one repo session-id)]
      (t/is (= session result))))
  (t/testing "session with specified id doesn't exist"
    (let [repo (sut/->SessionRepository *DB*)
          session-id "NOPE"
          result (get-one repo session-id)]
      (t/is (nil? result)))))

(t/deftest test-get-many
  (let [repo (sut/->SessionRepository *DB*)
        total (count *SESSIONS*)]
    (t/testing "get all sessions"
      (t/is (= *SESSIONS* (get-many repo))))
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
      (let [session (first *SESSIONS*)
            session-id (:session-id session)
            query (-> (sqlh/select :*)
                      (sqlh/where [:= :session-id session-id]))
            result (first (find repo query))]
        (t/is (= session result))))
    (t/testing "find all valid sessions"
      (let [sessions (filter #(true? (:is-valid %)) *SESSIONS*)
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
        (t/is (= session save-result))
        (t/is (= session (dissoc qry-result :session-attributes)))))
    (t/testing "Save a new session with additional attributes"
      (let [session (generate valid-session-gen)
            save-result (save repo session)
            qry-result (get-one repo (:session-id session))]
        (t/is (= session save-result))
        (t/is (= session qry-result))))
    (t/testing "Update an existing session"
      (let [session (generate valid-session-gen)
            save-result (save repo session)
            session (assoc session :is-valid false)
            update-result (save repo session)
            result (get-one repo (:session-id session))]
        (t/is (= (:is-valid session) (:is-valid result)))
        (t/is (= session result))))))
