(defproject dsbdp-via-bowerick "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-assorted-utils "1.18.2"]
                 [org.clojure/tools.cli "0.3.3"]
                 [dsbdp "0.4.0"]
                 [bowerick "2.1.1"]]
  :global-vars {*warn-on-reflection* true}
  :profiles  {:repl  {:dependencies  [[jonase/eastwood "0.2.2" :exclusions  [org.clojure/clojure]]]}}
  :plugins [[lein-cloverage "1.0.6"]]
;  :test2junit-output-dir "ghpages/test-results"
;  :test2junit-run-ant true  
;  :html5-docs-docs-dir "ghpages/doc"
;  :html5-docs-ns-includes #"^dsbdp.*"
;  :html5-docs-repository-url "https://github.com/ruedigergad/dsbdp-via-bowerick/blob/master"
  :aot :all
  :main dsbdp-via-bowerick.main)
