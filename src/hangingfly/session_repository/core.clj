(ns hangingfly.session-repository.core)

(defprotocol ISessionRepository
  (get-session [this sid])
  (get-sessions [this])
  (add-session [this session])
  (remove-session [this sid])
  (update-session [this session])
  (find-sessions [this query]))
