(ns clj-config.env-config-test
  (:require [clojure.test :refer :all]
            [clj-config :refer :all]
            [clj-config.test-helper :refer :all]))

(defn resetting-required-env [f]
  (reset! required-env #{})
  (f))

(use-fixtures :each silencing-info resetting-required-env)

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

(deftest deref-throws-when-config-is-uninitialized
  (eval `(defconfig :env {~'foo "OHAI"}))
  (is (thrown? clojure.lang.ExceptionInfo
               (get-in* config "OHAI" :ohai-value))))
