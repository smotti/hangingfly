(ns hangingfly.session-repository.core)

(defprotocol ISessionRepository
  (get-session [this sid])
  (get-sessions [this limit offset])
  (delete-session [this sid])
  (find-sessions [this query])
  (save-session [this session]))

(defn delete
  [repo sid]
  (delete-session repo sid))

(defn get-one
  [repo sid]
  (get-session repo sid))

(defn get-many
  [repo & {:keys [limit offset] :or {limit nil offset 0} :as opt}]
  (get-sessions repo limit offset))

(defn find
  [repo query]
  (find-sessions repo query))

(defn save
  [repo session]
  (save-session repo session))
