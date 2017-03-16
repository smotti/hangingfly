(ns hangingfly.session-repository.core)

(defprotocol ISessionRepository
  (get-session [this sid])
  (get-all-sessions [this])
  (add-session [this session])
  (remove-session [this sid])
  (update-session [this session])
  (execute-query [this query]))
