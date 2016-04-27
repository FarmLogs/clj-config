(defproject clj-config "1.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]]
  :repositories [["primedia"
                  {:url "http://nexus.idg.primedia.com/nexus/content/repositories/primedia"
                   :sign-releases false}]
                 ["farmlogs-internal" {:url "s3p://fl-maven-repo/mvn"
                                       :username ~(System/getenv "AMAZON_KEY")
                                       :passphrase ~(System/getenv "AMAZON_SECRET")}]]
  :signing {:gpg-key "164E1387"})
