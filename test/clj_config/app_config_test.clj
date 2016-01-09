(ns clj-config.app-config-test
  (:require [clojure.test :refer :all]
            [clojure.edn :as edn]
            [clj-config.app :as app]
            [clj-config.core :refer :all]
            [clj-config.test-helper :refer :all]))

(defn resetting [f]
  (reset! required-app-config #{})
  (alter-var-root #'app-config (constantly nil))
  (f))

(use-fixtures :each silencing-logging resetting)

(def expected
  {:dev {:sentry-dsn nil
         :web-server-threads 80
         :api-key "invariant"
         :false-value false
         :nested {:url "moarcats.gov"
                  :usr "mittens-dev"
                  :pwd "m30w"}}

   :ci {:sentry-dsn "ci/qa sentry dsn"
        :web-server-threads 40
        :api-key "invariant"
        :false-value false
        :nested {:url "moarcats.gov"
                 :usr "mittens-dev"
                 :pwd "m30w"}}

   :qa {:sentry-dsn "ci/qa sentry dsn"
        :web-server-threads 20
        :api-key "invariant"
        :false-value false
        :nested {:url "moarcats.gov"
                 :usr "mittens-qa"
                 :pwd "m30w"}}

   :staging {:sentry-dsn "default dsn"
             :web-server-threads 4
             :api-key "invariant"
             :false-value false
             :nested {:url "moarcats.gov"
                      :usr "mittens-default"
                      :pwd "m30w"}}

   :production {:sentry-dsn "prod sentry dsn"
                :web-server-threads 10
                :api-key "invariant"
                :false-value false
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
  (let [env {"APPLICATION_ENVIRONMENT" "dev"
             "CLJ_APP_CONFIG" "test/fixtures/app_config.edn"}]
    (swap! required-app-config conj :important-but-missing-value)
    (is (thrown? AssertionError (init-app-config! env)))))

(deftest false-values-satisfy-required-check
  (let [env {"APPLICATION_ENVIRONMENT" "dev"
             "CLJ_APP_CONFIG" "test/fixtures/app_config.edn"}]
    (swap! required-app-config conj :false-value)
    (is (init-app-config! env))))

(deftest reading-and-transforming-app-config
  (let [config-path "test/fixtures/app_config.edn"]
    (is (= (expected  :dev) (read-app-config config-path "dev")))
    (is (= (expected   :ci) (read-app-config config-path "ci")))
    (is (= (expected   :qa) (read-app-config config-path "qa")))
    (is (= (expected :production) (read-app-config config-path "production")))))

(deftest deref-app-config-values
  (let [env {"CLJ_APP_CONFIG" "test/fixtures/app_config.edn"
             "APPLICATION_ENVIRONMENT" "ci"}]
    (eval `(do (in-ns 'clj-config.app-config-test)
               (defconfig :app {~'sentry-dsn :sentry-dsn})))
    (init-app-config! env)
    (is (= "ci/qa sentry dsn" @@(resolve 'sentry-dsn)))))

(deftest no-env-config-file
  (let [env {"APPLICATION_ENVIRONMENT" "ci"}]
    (eval `(do (in-ns 'clj-config.app-config-test)
               (defconfig :app {~'sentry-dsn :sentry-dsn})))
    (is (thrown? java.io.FileNotFoundException (init-app-config! env)))))

(deftest no-app-config-file-without-defconfig
  (with-fake-env {}
    (init-app-config! {}) ;; should not throw
    (is (= 1 1))))

(deftest use-default-when-no-choice-for-env
  (let [config-path "test/fixtures/app_config.edn"]
    (is (= (expected :staging)
           (-> (slurp config-path)
               (edn/read-string)
               (app/prepare-config)
               (update-in [:envs] (fnil conj #{}) :staging) ;
               (app/resolve-app-config :staging))))))

(deftest defconfig-works-for-keypath
  (let [env {"CLJ_APP_CONFIG" "test/fixtures/app_config.edn"
             "APPLICATION_ENVIRONMENT" "ci"}]
    (eval `(do (in-ns 'clj-config.app-config-test)
               (defconfig :app {~'user [:nested :usr]})))
    (init-app-config! env)
    (is (= "mittens-dev" @@(resolve 'user)))))

(deftest defconfig-works-at-multiple-depths
  (let [env {"CLJ_APP_CONFIG" "test/fixtures/app_config.edn"
             "APPLICATION_ENVIRONMENT" "ci"}]
    (eval `(do (in-ns 'clj-config.app-config-test)
               (defconfig :app {~'ncfg    :nested
                                ~'user   [:nested :usr]
                                ~'pass   [:nested :pwd]})))
    (init-app-config! env)
    (is (=  {:url "moarcats.gov" :pwd "m30w" :usr "mittens-dev"}
            @@(resolve 'ncfg)))
    (is (= "mittens-dev" @@(resolve 'user)))
    (is (= "m30w"        @@(resolve 'pass)))))

(deftest re-init-updates-app-vars
  (let [env {"CLJ_APP_CONFIG" "test/fixtures/app_config.edn"
             "APPLICATION_ENVIRONMENT" "ci"}]
    (eval `(do (in-ns 'clj-config.app-config-test)
               (defconfig :app {~'user [:nested :usr]})))
    (init-app-config! env)
    (is (= "mittens-dev" @@(resolve 'user)))
    (init-app-config! (assoc env "APPLICATION_ENVIRONMENT" "qa"))
    (is (= "mittens-qa" @@(resolve 'user)))))
