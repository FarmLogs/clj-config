(defproject clj-config "0.4.4"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]]
  :repositories [["primedia"
                  {:url "http://nexus.idg.primedia.com/nexus/content/repositories/primedia"
                   :sign-releases false}]]
  :signing {:gpg-key "164E1387"})
