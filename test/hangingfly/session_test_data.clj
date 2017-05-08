(ns hangingfly.session-test-data)

(def absolute-sessions
  '({:session-id "n68", :start-time 1489648741570, :absolute-timeout 3600,
     :idle-timeout 10800, :renewal-timeout 10800, :end-time nil, :is-valid true
     :salted-id "n68" :previous-session-ids [] :cause-for-termination nil}
   {:session-id "j", :start-time 1489648937335, :absolute-timeout 3600,
    :idle-timeout 10800, :renewal-timeout 10800, :end-time nil, :is-valid true
    :salted-id "j" :previous-session-ids [] :cause-for-termination nil}
   {:session-id "qD", :start-time 1489648893582, :absolute-timeout 3600,
    :idle-timeout 10800, :renewal-timeout 10800, :end-time nil, :is-valid true
    :salted-id "qD" :previous-session-ids [] :cause-for-termination nil}
   {:session-id "9q3", :start-time 1489647108786, :absolute-timeout 3600,
    :idle-timeout 10800, :renewal-timeout 10800, :end-time nil, :is-valid true
    :salted-id "9q3" :previous-session-ids [] :cause-for-termination nil}
   {:session-id "Y", :start-time 1489648939234, :absolute-timeout 3600,
    :idle-timeout 10800, :renewal-timeout 10800, :end-time nil, :is-valid true
    :salted-id "Y" :previous-session-ids [] :cause-for-termination nil}
   {:session-id "qF9e", :start-time 1489648938631, :absolute-timeout 3600,
    :idle-timeout 10800, :renewal-timeout 10800, :end-time nil, :is-valid true
    :salted-id "qF9e" :previous-session-ids [] :cause-for-termination nil}
   {:session-id "78551f", :start-time 1489648935313, :absolute-timeout 3600,
    :idle-timeout 10800, :renewal-timeout 10800, :end-time nil, :is-valid true
    :salted-id "78551f" :previous-session-ids [] :cause-for-termination nil}
   {:session-id "ZCO", :start-time 1489648916283, :absolute-timeout 3600,
    :idle-timeout 10800, :renewal-timeout 10800, :end-time nil, :is-valid true
    :salted-id "ZCO" :previous-session-ids [] :cause-for-termination nil}
   {:session-id "tdjH12l", :start-time 1489648939233, :absolute-timeout 3600,
    :idle-timeout 10800, :renewal-timeout 10800, :end-time nil, :is-valid true
    :salted-id "tdjH12l" :previous-session-ids [] :cause-for-termination nil}
   {:session-id "V3VVg7", :start-time 1489648939226, :absolute-timeout 3600,
    :idle-timeout 10800, :renewal-timeout 10800, :end-time nil, :is-valid true
    :salted-id "V3VVg7" :previous-session-ids [] :cause-for-termination nil}))

(def idle-sessions
  '({:session-id "S9z", :start-time 1489651709345, :absolute-timeout 3600,
     :idle-timeout 1200, :renewal-timeout 3600, :end-time nil, :is-valid true}
    {:session-id "n", :start-time 1489651709352, :absolute-timeout 3600,
     :idle-timeout 1200, :renewal-timeout 3600, :end-time nil, :is-valid true}
    {:session-id "1m", :start-time 1489651709248, :absolute-timeout 3600,
     :idle-timeout 1200, :renewal-timeout 3600, :end-time nil, :is-valid true}
    {:session-id "10", :start-time 1489651709014, :absolute-timeout 3600,
     :idle-timeout 1200, :renewal-timeout 3600, :end-time nil, :is-valid true}
    {:session-id "L2S", :start-time 1489651661460, :absolute-timeout 3600,
     :idle-timeout 1200, :renewal-timeout 3600, :end-time nil, :is-valid true}
    {:session-id "P", :start-time 1489650874553, :absolute-timeout 3600,
     :idle-timeout 1200, :renewal-timeout 3600, :end-time nil, :is-valid true}
    {:session-id "oH", :start-time 1489651708482, :absolute-timeout 3600,
     :idle-timeout 1200, :renewal-timeout 3600, :end-time nil, :is-valid true}
    {:session-id "458pf", :start-time 1489651709292, :absolute-timeout 3600,
     :idle-timeout 1200, :renewal-timeout 3600, :end-time nil, :is-valid true}
    {:session-id "M22nT", :start-time 1489651550656, :absolute-timeout 3600,
     :idle-timeout 1200, :renewal-timeout 3600, :end-time nil, :is-valid true}
    {:session-id "6W", :start-time 1489650783797, :absolute-timeout 3600,
     :idle-timeout 1200, :renewal-timeout 3600, :end-time nil, :is-valid true}))

(def renewal-sessions
  '({:session-id "h", :start-time 1489651843305, :absolute-timeout 900,
    :renewal-timeout 300, :end-time nil, :is-valid true, :idle-timout 900}
   {:session-id "D", :start-time 1489651847604, :absolute-timeout 900,
    :renewal-timeout 300, :end-time nil, :is-valid true, :idle-timout 900}
   {:session-id "qFd", :start-time 1489651843971, :absolute-timeout 900,
    :renewal-timeout 300, :end-time nil, :is-valid true, :idle-timout 900}
   {:session-id "vU", :start-time 1489651847605, :absolute-timeout 900,
    :renewal-timeout 300, :end-time nil, :is-valid true, :idle-timout 900}
   {:session-id "eW", :start-time 1489651790627, :absolute-timeout 900,
    :idle-timeout 900, :renewal-timeout 300, :end-time nil, :is-valid true}
   {:session-id "nJAG9", :start-time 1489651847605, :absolute-timeout 900,
    :renewal-timeout 300, :end-time nil, :is-valid true, :idle-timeout 900}
   {:session-id "1", :start-time 1489651847597, :absolute-timeout 900,
    :renewal-timeout 300, :end-time nil, :is-valid true, :idle-timout 900}
   {:session-id "6v58j2t", :start-time 1489651847599, :absolute-timeout 900,
    :renewal-timeout 300, :end-time nil, :is-valid true, :idle-timout 900}
   {:session-id "iCz68Pp", :start-time 1489651847465, :absolute-timeout 900,
    :renewal-timeout 300, :end-time nil, :is-valid true, :idle-timout 900}
   {:session-id "Dm", :start-time 1489651591486, :absolute-timeout 900,
    :renewal-timeout 300, :end-time nil, :is-valid true, :idle-timout 900}))
