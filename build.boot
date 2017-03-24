(set-env!
  :source-paths #{"src" "test"}
  :dependencies '[[org.clojure/core.async "0.3.442"]
                  [adzerk/boot-test "1.2.0" :scope "test"]
                  [org.clojure/test.check "0.9.0" :scope "test"]])

(require '[adzerk.boot-test :refer :all])

(task-options!
  pom {:project 'hangingfly
       :version "0.1.0"
       :license "GPLv3"})

(deftask build
  []
  (comp (pom) (jar) (target)))
