(ns clj-config.app-config-test
  (:require [clojure.test :refer :all]
            [clj-config :refer :all]
            [clj-config.test-helper :refer :all]))

(defn resetting-required-vars [f]
  (reset! required-app-config #{})
  (f))

(use-fixtures :each silencing-info resetting-required-vars)

(deftest defappconfig-populates-required-vars
  (eval `(defenv :app {~'default-port :java-listening-port}))
  (is (= #{:java-listening-port} @required-app-config))

  (eval `(defenv :app {~'foo :foo-value}))
  (is (= #{:java-listening-port :foo-value} @required-app-config)))

(deftest init-app-config-verifies-required-values
  (swap! required-app-config conj :important-but-missing-value)
  (is (thrown? AssertionError (init-app-config! "dev"))))

(deftest reading-and-transforming-app-config
  (let [config-path "test/fixtures/app_config.edn"]
    (is (= {:sentry-dsn nil :web-server-threads 80 :api-key "invariant"}
           (read-app-config config-path "dev")))
    (is (= {:sentry-dsn "ci/qa sentry dsn" :web-server-threads 40 :api-key "invariant"}
           (read-app-config config-path "ci")))
    (is (= {:sentry-dsn "ci/qa sentry dsn" :web-server-threads 20 :api-key "invariant"}
           (read-app-config config-path "qa")))
    (is (= {:sentry-dsn "prod sentry dsn" :web-server-threads 10  :api-key "invariant"}
           (read-app-config config-path "prod")))))

(deftest deref-app-config-values
  (with-fake-env {"APP_OWNED_CONFIG_EDN_FILE" "test/fixtures/app_config.edn"}
    (eval `(do (in-ns 'clj-config.app-config-test)
               (defenv :app {~'sentry-dsn :sentry-dsn})))
    (init-app-config! "ci")
    (is (= "ci/qa sentry dsn" @@(resolve 'sentry-dsn)))))

(deftest no-env-config-file
  (with-fake-env {}
    (eval `(do (in-ns 'clj-config.app-config-test)
               (defenv :app {~'sentry-dsn :sentry-dsn})))
    (is (thrown? AssertionError (init-app-config! "ci")))))

(deftest no-app-config-file
  (with-fake-env {}
    (eval `(do (in-ns 'clj-config.app-config-test)
               (defenv :app {~'sentry-dsn :sentry-dsn})))
    (is (thrown? AssertionError (init-app-config! "ci")))))

(deftest no-app-config-file-without-defappconfig
  (with-fake-env {}
    (init-app-config! "ci") ;; should not throw
    (is (= 1 1))))
