(ns clj-config.env-config-test
  (:require [clojure.test :refer :all]
            [clj-config.core :refer :all]
            [clj-config.test-helper :refer :all]))

(defn resetting [f]
  (reset! required-env #{})
  (alter-var-root #'config (constantly nil))
  (f))

(use-fixtures :each silencing-logging resetting)

(deftest read-env-with-ENV_FILE
  (with-fake-env {"FOO" "BAR"
                  "ENV_FILE" "test/fixtures/sample_env.cfg"}
    (let [computed-env (read-env "test/fixtures")]
      (is (= "BAR" (get-in* computed-env "FOO")))
      (is (= "got it" (get-in* computed-env "FROM_INFRA_FILE"))))))

(deftest read-env-without-ENV_FILE
  (with-fake-env {"FOO" "BAR"}
    (let [computed-env (read-env "test/fixtures")]
      (is (= "BAR" (get-in* computed-env "FOO")))
      (is (= "hi" (get-in* computed-env "FROM_ENV_LOCAL")))
      (is (= "hello" (get-in* computed-env "FROM_ENV"))))))

(deftest environment-variables-beat-files
  (with-fake-env {"FROM_INFRA_FILE" "overridden"
                  "ENV_FILE" "test/fixtures/sample_env.cfg"}
    (let [computed-env (read-env "test/fixtures")]
      (is (= "overridden" (get-in* computed-env "FROM_INFRA_FILE"))))))

(deftest defconfig-populates-required-env
  (eval `(defconfig :env {~'default-port "JAVA_LISTENING_PORT"}))
  (is (= #{"JAVA_LISTENING_PORT"} @required-env))

  (eval `(defconfig :env {~'foo "FOO_VALUE"}))
  (is (= #{"JAVA_LISTENING_PORT" "FOO_VALUE"} @required-env)))

(deftest init-verifies-presence-of-required-values
  (swap! required-env conj "IMPORTANT_BUT_MISSING_VALUE")
  (is (thrown? AssertionError (init! "test/fixtures"))))

(deftest re-init-updates-env-vars
  (with-fake-env {"ENV_FILE" "test/fixtures/sample_env.cfg"
                  "CLJ_APP_CONFIG" "test/fixtures/app_config.edn"}
    (eval `(do (in-ns 'clj-config.env-config-test)
               (defconfig :env {~'env-var-source "ENV_VAR_SOURCE"})))
    (init! "test/fixtures"))
  (is (= "sample_env.cfg" @@(resolve 'env-var-source)))
  (with-fake-env {"ENV_FILE" "test/fixtures/.env.local"
                  "CLJ_APP_CONFIG" "test/fixtures/app_config.edn"}
    (init! "test/fixtures"))
  (is (= ".env.local" @@(resolve 'env-var-source))))

(deftest deref-throws-when-config-is-uninitialized
  (eval `(do (in-ns 'clj-config.env-config-test)
             (defconfig :env {~'foo "OHAI"})))
  (is (thrown? clojure.lang.ExceptionInfo
               @@(resolve 'foo))))

