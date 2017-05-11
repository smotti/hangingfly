(set-env!
  :resource-paths #{"resources"}
  :source-paths #{"src" "test"}
  :dependencies '[[org.clojure/core.async "0.3.442"]
                  [org.clojure/java.jdbc "0.7.0-alpha3"]
                  [camel-snake-kebab "0.4.0"]
                  [honeysql "0.8.2"]
                  [adzerk/boot-test "1.2.0" :scope "test"]
                  [org.clojure/test.check "0.9.0" :scope "test"]
                  [org.xerial/sqlite-jdbc "3.16.1" :scope "test"]
                  [org.flywaydb/flyway-core "4.2.0" :scope "test"]
                  [com.opentable.components/otj-pg-embedded "0.7.1" :scope "test"]])

(require '[adzerk.boot-test :refer :all])

(task-options!
  pom {:project 'hangingfly
       :version "0.1.0"
       :license "GPLv3"})

(deftask build
  []
  (comp (pom) (jar) (target)))
