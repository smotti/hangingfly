(ns hangingfly.session-repository.rdbms
  (:import java.sql.Connection)
  (:require [hangingfly.session-repository.core :refer [delete-session
                                                        get-session
                                                        ISessionRepository]]
            [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as cstr]
            [honeysql.core :as sql]
            [honeysql.helpers :as sqlh]))

(declare add-session keys->snake_case select-attr-value update-session)

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

(defn- insert-attr-cmd
  [attr]
  (-> (sqlh/insert-into :session-attributes)
      (sqlh/values [attr])))

(defn- attr-xform
  [id [n v]]
  {:session-id id :name (name n)
   :value (if (not (instance? java.lang.Boolean v)) v (bool->int v))
   :data-type (data-type? v)})

(defn insert-attr-value-cmd
  [v]
  (-> (sqlh/insert-into :session-attribute-value)
      (sqlh/values [v])))

(defn- insert-attr-xform
  [id [n v]]
  {:session-id id :name (name n) :data-type (data-type? v)})

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

(defn- sql-delete-session
  [id]
  (-> (sqlh/delete-from :session)
      (sqlh/where [:= :session_id id])))

(defn- select-attrs
  [id]
  (-> (sqlh/select :name
                   :data_type
                   :value.string
                   :value.number
                   :value.date
                   :value.bool)
      (sqlh/from [:session_attributes :attrs])
      (sqlh/where [:= :session_id id])
      (sqlh/left-join [select-attr-value :value] [:= :value.attribute_id :attrs.id])))

(def ^:private select-attr-value
  (-> (sqlh/select :attribute_id :string :number :date :bool)
      (sqlh/from :session_attribute_value)))

(def select-many
  (-> (sqlh/select :*)
      (sqlh/from :session)))

(defn- select-session
  [id]
  (-> (sqlh/select :*)
      (sqlh/from :session)
      (sqlh/where [:= :session_id id])))

(def ^:private select-xform
  (comp keys->kebab-case
        #(assoc %
                :is_valid (int->bool (:is_valid %))
                :previous_session_ids (let [ids (:previous_session_ids %)]
                                        (if (empty? ids)
                                          []
                                          (cstr/split ids #","))))))

(defrecord SessionRepository [conn]
  ISessionRepository
  (delete-session
    [this sid]
    (let [db-spec (:conn this)]
      (first (jdbc/execute! db-spec (sql/format (sql-delete-session sid))))))
  (get-session
    [this sid]
    (let [db-spec (:conn this)
          session (if-let [s (first (jdbc/query db-spec
                                                (sql/format
                                                 (select-session sid))))]
                    (select-xform s)
                    nil)
          xform #(let [attr-name (keyword (:name %))
                       attr-type (keyword (:data_type %))
                       attr-value (condp = attr-type
                                    :bool (int->bool (attr-type %))
                                    :date (java.util.Date. (attr-type %))
                                    (attr-type %))
                       attr [attr-name attr-value]]
                   attr)
          attrs (->> (jdbc/query db-spec (sql/format (select-attrs sid)))
                     (map xform)
                     (into {}))]
      (when session
        (assoc session :session-attributes attrs))))
  (get-sessions
    [this limit offset]
    (let [db-spec (:conn this)
          base (-> select-many
                   (sqlh/offset offset))
          query (if limit
                  (-> base
                      (sqlh/limit limit))
                  (-> base
                      (sqlh/limit (-> (sqlh/select :%count.*)
                                      (sqlh/from :session)))))]
      (jdbc/query db-spec (sql/format query))))
  (find-sessions
    [this query]
    (let [db-spec (:conn this)
          query (-> query
                    (sqlh/from :session))
          result (jdbc/query db-spec (sql/format query))]
      (map select-xform result)))
  ;; TODO: Observable behavior should be different not just returning a 1 or 0
  (save-session
    [{db-spec :conn :as this}
     {:keys [session-id session-attributes] :as session}]
    (if-let [s (get-session this session-id)]
      (update-session this session)
      (add-session db-spec session))))

(defn- add-session
  [db-spec {:keys [session-id session-attributes] :as session}]
  (let [attr-insert-fn (fn [conn attr]
                         (let [insert-cmd (comp insert-attr-cmd
                                                #(dissoc % :value))
                               stmt (sql/format (insert-cmd attr))
                               id (-> (jdbc/db-do-prepared-return-keys conn stmt)
                                      first
                                      val)]
                           (assoc attr :attribute-id id)))
        session-insert-cmd (-> (sqlh/insert-into :session)
                               (sqlh/values [(insert-xform
                                              (dissoc session
                                                      :session-attributes))]))
        attrs (mapv #(attr-xform session-id %)
                    (:session-attributes session))
        return (first (jdbc/db-do-prepared db-spec (sql/format session-insert-cmd)))]
    (if (= 1 return)
      (let [attrs (jdbc/with-db-transaction [conn db-spec]
                    (doall (mapv #(attr-insert-fn conn %) attrs)))
            attr-value-insert-fn (fn [conn value]
                                   ((comp #(jdbc/db-do-prepared conn %)
                                          sql/format
                                          insert-attr-value-cmd)
                                    value))
            attrs-values (mapv attr->attr-value attrs)]
        (jdbc/with-db-transaction [conn db-spec]
          (doseq [value attrs-values]
            (attr-value-insert-fn conn value)))
        return)
      return)))

(defn make-session-repo
  [db-conn]
  (->SessionRepository db-conn))

(defn- update-session
  [{db-spec :conn :as this} {:keys [session-id ] :as session}]
  (jdbc/with-db-transaction [conn db-spec]
    (delete-session this session-id)
    (add-session conn session)))
