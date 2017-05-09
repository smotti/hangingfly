(ns hangingfly.session-repository.core)

(defn wrap-sql-exception
  [f]
  (fn [repo & args]
    (try
      (apply f repo args)
      (catch java.sql.SQLException e
        (throw (ex-info (.getMessage e) {:cause :repository-exception
                                         :exception e}))))))

(defprotocol ISessionRepository
  (delete! [this sid])
  (get-one [this sid])
  (get-many [this limit offset])
  (find [this query])
  (save! [this session]))

(def ^:private do-delete!
  (-> delete!
      wrap-sql-exception))

(defn delete-session!
  [repo sid]
  (do-delete! repo sid))

(def ^:private do-get-one
  (-> get-one
      wrap-sql-exception))

(defn get-session
  [repo sid]
  (do-get-one repo sid))

(def ^:private do-get-many
  (-> get-many
      wrap-sql-exception))

(defn get-many-sessions
  [repo & {:keys [limit offset] :or {limit nil offset 0} :as opt}]
  (do-get-many repo limit offset))

(def ^:private do-find
  (-> find
      wrap-sql-exception))

(defn find-sessions
  [repo query]
  (do-find repo query))

(def ^:private do-save!
  (-> save!
      wrap-sql-exception))

(defn save-session!
  [repo session]
  (do-save! repo session))
