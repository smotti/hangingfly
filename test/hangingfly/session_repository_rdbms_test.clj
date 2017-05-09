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

(t/deftest test-delete-session!
  (let [repo (sut/->SessionRepository *DB*)]
    (t/testing "delete a existing session"
      (let [session-id (:session-id (first *SESSIONS*))
            result (delete-session! repo session-id)]
        (t/is (= session-id result))))
    (t/testing "delete a non-existing session"
      (let [session-id "NOPE"
            result (delete-session! repo session-id)]
        (t/is (nil? result))))
    (t/testing "exception handling"
      (let [session-id "TEST"
            msg #"TEST"
            stub (reify ISessionRepository (delete! [_ _] (throw (java.sql.SQLException. "TEST"))))]
        (t/is (thrown-with-msg? clojure.lang.ExceptionInfo msg
                                (delete-session! stub session-id)))))))

(t/deftest test-get-session
  (let [repo (sut/->SessionRepository *DB*)]
    (t/testing "session with specified id exists"
      (let [session (first *SESSIONS*)
            session-id (:session-id session)
            result (get-session repo session-id)]
        (t/is (= session result))))
    (t/testing "session with specified id doesn't exist"
      (let [session-id "NOPE"
            result (get-session repo session-id)]
        (t/is (nil? result))))
    (t/testing "exception handling"
      (let [sid "TEST"
            msg #"TEST"
            stub (reify ISessionRepository (get-one [_ _] (throw (java.sql.SQLException. "TEST"))))]
        (t/is (thrown-with-msg? clojure.lang.ExceptionInfo msg
                                (get-session stub sid)))))))

(t/deftest test-get-many-session
  (let [repo (sut/->SessionRepository *DB*)
        total (count *SESSIONS*)]
    (t/testing "get all sessions"
      (t/is (= *SESSIONS* (get-many-sessions repo))))
    (t/testing "get a limited amount of sessions"
      (let [limit 5
            result (get-many-sessions repo :limit limit)]
        (t/is (= limit (count result)))))
    (t/testing "get all sessions starting from an offset"
      (let [offset 5
            amount (- total offset)
            result (get-many-sessions repo :offset offset)]
        (t/is (= amount (count result)))))
    (t/testing "get a limited amount of sessions starting from an offset"
      (let [limit 2
            offset 5
            result (get-many-sessions repo :limit limit :offset offset)]
        (t/is (= limit (count result)))))
    (t/testing "exception handling"
      (let [stub (reify ISessionRepository (get-many [_ _ _] (throw (java.sql.SQLException. "TEST"))))]
        (t/is (thrown? clojure.lang.ExceptionInfo
                       (get-many-sessions stub)))))))

(t/deftest test-find-sessions
  (let [repo (sut/->SessionRepository *DB*)]
    (t/testing "find session by id"
      (let [session (first *SESSIONS*)
            session-id (:session-id session)
            query (-> (sqlh/select :*)
                      (sqlh/where [:= :session-id session-id]))
            result (first (find-sessions repo query))]
        (t/is (= session result))))
    (t/testing "find all valid sessions"
      (let [sessions (filter #(true? (:is-valid %)) *SESSIONS*)
            query (-> (sqlh/select :*)
                      (sqlh/where [:= :is-valid 1]))
            result (find-sessions repo query)]
        (t/is (= (count sessions) (count result)))
        (t/is (= sessions result))))
    (t/testing "find all sessions that are a child session")
    (t/testing "find all sessions terminated because of idleing")
    (t/testing "find all sessions terminated due to absolute timeout")
    (t/testing "exception handling"
      (let [stub (reify ISessionRepository (find [_ _] (throw (java.sql.SQLException. "TEST"))))]
        (t/is (thrown? clojure.lang.ExceptionInfo
                       (find-sessions stub 'qry)))))))

(t/deftest test-save-session!
  (let [repo (sut/->SessionRepository *DB*)]
    (t/testing "Save new session without any additional attributes"
      (let [session (dissoc (generate valid-session-gen) :session-attributes)
            save-result (save-session! repo session)
            qry-result (get-session repo (:session-id session))]
        (t/is (= session save-result))
        (t/is (= session (dissoc qry-result :session-attributes)))))
    (t/testing "Save a new session with additional attributes"
      (let [session (generate valid-session-gen)
            save-result (save-session! repo session)
            qry-result (get-session repo (:session-id session))]
        (t/is (= session save-result))
        (t/is (= session qry-result))))
    (t/testing "Update an existing session"
      (let [session (generate valid-session-gen)
            save-result (save-session! repo session)
            session (assoc session :is-valid false)
            update-result (save-session! repo session)
            result (get-session repo (:session-id session))]
        (t/is (= (:is-valid session) (:is-valid result)))
        (t/is (= session result))))
    (t/testing "exception handling"
      (let [stub (reify ISessionRepository (save! [_ _] (throw (java.sql.SQLException. "TEST"))))]
        (t/is (thrown? clojure.lang.ExceptionInfo
                       (save-session! stub 'session)))))))
