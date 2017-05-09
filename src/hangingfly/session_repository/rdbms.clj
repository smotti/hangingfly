(ns hangingfly.session-repository.rdbms
  (:import java.sql.Connection)
  (:require [hangingfly.session-repository.core :refer [delete!
                                                        get-one
                                                        ISessionRepository]]
            [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as cstr]
            [honeysql.core :as sql]
            [honeysql.helpers :as sqlh]))

;;;
;;; Helpers
;;;

(defn attr->attr-value
  [{:keys [attribute-id data-type value] :as attr}]
  {(keyword data-type) value :attribute-id attribute-id})

(defn- bool->int
  [x]
  (condp = x
    false 0
    true 1))

(defn data-type?
  [v]
  (condp #(instance? %1 %2) v
    java.lang.Number "number"
    java.lang.String "string"
    java.util.Date "date"
    java.lang.Boolean "bool"))

(defn- int->bool
  [x]
  (condp = x
    0 false
    1 true))

(defn- insert-attr-xform
  [id [n v]]
  {:session-id id
   :name (name n)
   :value (if (not (instance? java.lang.Boolean v)) v (bool->int v))
   :data-type (data-type? v)})

(declare keys->snake_case)
(def ^:private insert-xform
  (comp keys->snake_case
        #(assoc %
                :is-valid (bool->int (:is-valid %))
                :previous-session-ids (cstr/join ","
                                                 (:previous-session-ids %)))
        #(into {} %)))

(defn- keys->kebab-case
  [m] (reduce-kv #(assoc %1 (->kebab-case %2) %3) {} m))

(defn- keys->snake_case
  [m] (reduce-kv #(assoc %1 (->snake_case %2) %3) {} m))

(defn- select-attr-xform
  [a]
  (let [attr-name (keyword (:name a))
        attr-type (keyword (:data_type a))
        attr-value (condp = attr-type
                     :bool (int->bool (attr-type a))
                     :date (java.util.Date. (attr-type a))
                     (attr-type a))
        attr [attr-name attr-value]]
    attr))

(def ^:private select-xform
  (comp keys->kebab-case
        #(assoc %
                :is_valid (int->bool (:is_valid %))
                :previous_session_ids (let [ids (:previous_session_ids %)]
                                        (if (empty? ids)
                                          []
                                          (cstr/split ids #","))))))

;;;
;;; SQL CMDs & QUERIEs
;;;

(defn- sql-delete-session-cmd
  [id]
  (-> (sqlh/delete-from :session)
      (sqlh/where [:= :session_id id])))

(defn- sql-insert-attr-cmd
  [attr]
  (-> (sqlh/insert-into :session-attributes)
      (sqlh/values [(dissoc attr :value)])))

(defn- sql-insert-attr-value-cmd
  [v]
  (-> (sqlh/insert-into :session-attribute-value)
      (sqlh/values [v])))

(defn- sql-insert-session-cmd
  [s]
  (-> (sqlh/insert-into :session)
      (sqlh/values [(insert-xform (dissoc s :session-attributes))])))

(declare sql-select-attr-value-qry)
(defn- sql-select-attrs-qry
  [id]
  (-> (sqlh/select :name
                   :data_type
                   :value.string
                   :value.number
                   :value.date
                   :value.bool)
      (sqlh/from [:session_attributes :attrs])
      (sqlh/where [:= :session_id id])
      (sqlh/left-join [sql-select-attr-value-qry :value] [:= :value.attribute_id :attrs.id])))

(def ^:private sql-select-attr-value-qry
  (-> (sqlh/select :attribute_id :string :number :date :bool)
      (sqlh/from :session_attribute_value)))

(def sql-select-many-qry
  (-> (sqlh/select :*)
      (sqlh/from :session)))

(defn- sql-select-session-qry
  [id]
  (-> (sqlh/select :*)
      (sqlh/from :session)
      (sqlh/where [:= :session_id id])))

;;;
;;; SESSION REPOSITORY
;;;

(declare add-session get-session-attributes update-session)
(defrecord SessionRepository [conn]
  ISessionRepository
  (delete!
    [{db-spec :conn :as this} sid]
    (let [result (first (jdbc/execute! db-spec
                                       (sql/format
                                        (sql-delete-session-cmd sid))))]
      (when (= 1 result)
        sid)))

  (get-one
    [{db-spec :conn :as this} sid]
    (jdbc/with-db-connection [conn db-spec]
      (let [session (if-let [s (first (jdbc/query conn
                                                  (sql/format
                                                   (sql-select-session-qry sid))))]
                      (select-xform s)
                      nil)
            attrs (->> (jdbc/query conn (sql/format (sql-select-attrs-qry sid)))
                       (map select-attr-xform)
                       (into {}))]
        (when session
          (assoc session :session-attributes attrs)))))

  (get-many
    [{db-spec :conn :as this} limit offset]
    (let [base (-> sql-select-many-qry
                   (sqlh/offset offset))
          query (if limit
                  (-> base
                      (sqlh/limit limit))
                  (-> base
                      (sqlh/limit (-> (sqlh/select :%count.*)
                                      (sqlh/from :session)))))]
      (jdbc/with-db-connection [conn db-spec]
        (let [sessions (jdbc/query conn (sql/format query))]
          (doall (mapv #(get-session-attributes conn %) sessions))))))

  (find
    [{db-spec :conn :as this} query]
    (let [query (-> query
                    (sqlh/from :session))]
      (jdbc/with-db-connection [conn db-spec]
        (let [sessions (jdbc/query conn (sql/format query))]
          (doall (mapv #(get-session-attributes conn %) sessions))))))

  (save!
    [this {:keys [session-id session-attributes] :as session}]
    (if-let [s (get-one this session-id)]
      (update-session this session)
      (add-session this session))))

(defn- add-session
  [{db-spec :conn :as this} session]
  (letfn [(insert-session [conn s]
            (let [qry (sql/format (sql-insert-session-cmd s))]
              (jdbc/db-do-prepared conn qry)
              s))
          (insert-attributes [conn {:keys [session-id] :as s}]
            (let [as (mapv #(insert-attr-xform session-id %)
                           (:session-attributes s))
                  qry #(sql/format (sql-insert-attr-cmd %))]
              (doall (mapv #(->> (jdbc/db-do-prepared-return-keys conn (qry %))
                                 first
                                 val
                                 (assoc % :attribute-id))
                           as))))
          (insert-attributes-values [conn as]
            (let [vs (mapv attr->attr-value as)
                  qry #(sql/format (sql-insert-attr-value-cmd %))]
              (doall (mapv #(do (jdbc/db-do-prepared conn (qry %))
                                %)
                           vs))))]
    (jdbc/with-db-transaction [conn db-spec]
      (->> session
           (insert-session conn)
           (insert-attributes conn)
           (insert-attributes-values conn))))
  session)

(defn- get-session-attributes
  [conn s]
  (->> (jdbc/query conn (sql/format (sql-select-attrs-qry (:session_id s))))
       (map select-attr-xform)
       (into {})
       (assoc s :session-attributes)
       select-xform))

(defn make-session-repo
  [db-conn]
  (->SessionRepository db-conn))

(defn- update-session
  [this {:keys [session-id ] :as session}]
  (delete! this session-id)
  (add-session this session))
