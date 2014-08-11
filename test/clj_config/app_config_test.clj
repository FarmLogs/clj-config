(ns clj-config.app-config-test
  (:require [clojure.test :refer :all]
            [clojure.edn :as edn]
            [clj-config :refer :all]
            [clj-config.app :as app]
            [clj-config.test-helper :refer :all]))

(defn resetting-required-vars [f]
  (reset! required-app-config #{})
  (f))

(use-fixtures :each silencing-info resetting-required-vars)

(def expected
  {:dev {:sentry-dsn nil
         :web-server-threads 80
         :api-key "invariant"
         :nested {:url "moarcats.gov"
                  :usr "mittens-dev"
                  :pwd "m30w"}}

   :ci {:sentry-dsn "ci/qa sentry dsn"
        :web-server-threads 40
        :api-key "invariant"
        :nested {:url "moarcats.gov"
                 :usr "mittens-dev"
                 :pwd "m30w"}}

   :qa {:sentry-dsn "ci/qa sentry dsn"
        :web-server-threads 20
        :api-key "invariant"
        :nested {:url "moarcats.gov"
                 :usr "mittens-qa"
                 :pwd "m30w"}}

   :staging {:sentry-dsn "default dsn"
             :web-server-threads 4
             :api-key "invariant"
             :nested {:url "moarcats.gov"
                      :usr "mittens-default"
                      :pwd "m30w"}}

   :production
   {:sentry-dsn "prod sentry dsn"
    :web-server-threads 10
    :api-key "invariant"
    :nested {:url "moarcats.gov"
             :usr "mittens-prod"
             :pwd "m30w"}}})

(deftest defconfig-populates-required-vars
  (eval `(defconfig :app {~'default-port :java-listening-port}))
  (is (= #{:java-listening-port} @required-app-config))

  (eval `(defconfig :app {~'foo :foo-value}))
  (is (= #{:java-listening-port :foo-value} @required-app-config))

  (testing "correctly handles key-path"
    (eval `(defconfig :app {~'bar [:key :path]}))
    (is (= #{:java-listening-port :foo-value [:key :path]} @required-app-config))))

(deftest init-app-config-verifies-required-values
  (swap! required-app-config conj :important-but-missing-value)
  (is (thrown? AssertionError (init-app-config! "dev"))))

(deftest reading-and-transforming-app-config
  (let [config-path "test/fixtures/app_config.edn"]
    (is (= (expected  :dev) (read-app-config config-path "dev")))
    (is (= (expected   :ci) (read-app-config config-path "ci")))
    (is (= (expected   :qa) (read-app-config config-path "qa")))
    (is (= (expected :production) (read-app-config config-path "production")))))

(deftest deref-app-config-values
  (with-fake-env {"CLJ_APP_CONFIG" "test/fixtures/app_config.edn"}
    (eval `(do (in-ns 'clj-config.app-config-test)
               (defconfig :app {~'sentry-dsn :sentry-dsn})))
    (init-app-config! "ci")
    (is (= "ci/qa sentry dsn" @@(resolve 'sentry-dsn)))))

(deftest no-env-config-file
  (with-fake-env {}
    (eval `(do (in-ns 'clj-config.app-config-test)
               (defconfig :app {~'sentry-dsn :sentry-dsn})))
    (is (thrown? AssertionError (init-app-config! "ci")))))

(deftest no-app-config-file
  (with-fake-env {}
    (eval `(do (in-ns 'clj-config.app-config-test)
               (defconfig :app {~'sentry-dsn :sentry-dsn})))
    (is (thrown? AssertionError (init-app-config! "ci")))))

(deftest no-app-config-file-without-defconfig
  (with-fake-env {}
    (init-app-config! "ci") ;; should not throw
    (is (= 1 1))))

(deftest use-default-when-no-choice-for-env
  (let [config-path "test/fixtures/app_config.edn"]
    (is (= (expected :staging)
           (->> (slurp config-path)
                (edn/read-string)
                (app/resolve-app-config {:default :default
                                         :envs #{:ci :dev :qa :production :staging}}
                                        :staging))))))
(deftest defconfig-works-for-keypath
  (with-fake-env {"CLJ_APP_CONFIG" "test/fixtures/app_config.edn"}
    (eval `(do (in-ns 'clj-config.app-config-test)
               (defconfig :app {~'user [:nested :usr]})))
    (init-app-config! "ci")
    (is (= "mittens-dev" @@(resolve 'user)))))


(deftest defconfig-works-at-multiple-depths
  (with-fake-env {"CLJ_APP_CONFIG" "test/fixtures/app_config.edn"}
    (eval `(do (in-ns 'clj-config.app-config-test)
               (defconfig :app {~'ncfg :nested
                                ~'user   [:nested :usr]
                                ~'pass   [:nested :pwd]})))
    (init-app-config! "ci")
    (is (=  {:url "moarcats.gov" :pwd "m30w" :usr "mittens-dev"}
            @@(resolve 'ncfg)))
    (is (= "mittens-dev" @@(resolve 'user)))
    (is (= "m30w"        @@(resolve 'pass)))))



