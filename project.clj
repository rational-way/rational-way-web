(defproject rational-way-web "0.1.0-SNAPSHOT"
  :description "Sources for my rational-way.com web page."
  :url "http://rational-way.com"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [hiccup "1.0.5"]
                 [markdown-clj "0.9.88"]
                 [clj-time "0.11.0"]
                 [environ "1.0.2"]]
  :plugins [[lein-environ "1.0.2"]]
  :main rationalway.web.core
  :profiles {:prod {:env {:canonical-url "http://rational-way.github.io/"}}})
