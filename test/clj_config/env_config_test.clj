(ns clj-config.env-config-test
  (:require [clojure.test :refer :all]
            [clj-config :refer :all]
            [clj-config.test-helper :refer :all]))

(defn resetting-required-vars [f]
  (reset! required-vars #{})
  (f))

(use-fixtures :each silencing-info resetting-required-vars)

(deftest get-env-with-ENV_FILE
  (with-fake-env {"FOO" "BAR"
                  "ENV_FILE" "test/fixtures/sample_env.cfg"}
    (let [computed-env (get-env "test/fixtures")]
      (is (= "BAR" (get-var computed-env "FOO")))
      (is (= "got it" (get-var computed-env "FROM_INFRA_FILE"))))))

(deftest get-env-without-ENV_FILE
  (with-fake-env {"FOO" "BAR"}
    (let [computed-env (get-env "test/fixtures")]
      (is (= "BAR" (get-var computed-env "FOO")))
      (is (= "hi" (get-var computed-env "FROM_ENV_LOCAL")))
      (is (= "hello" (get-var computed-env "FROM_ENV"))))))

(deftest environment-variables-beat-files
  (with-fake-env {"FROM_INFRA_FILE" "overridden"
                  "ENV_FILE" "test/fixtures/sample_env.cfg"}
    (let [computed-env (get-env "test/fixtures")]
      (is (= "overridden" (get-var computed-env "FROM_INFRA_FILE"))))) )

(deftest defconfig-populates-required-vars
  (eval `(defconfig ~'default-port "JAVA_LISTENING_PORT"))
  (is (= #{"JAVA_LISTENING_PORT"} @required-vars))

  (eval `(defconfig ~'foo "FOO_VALUE"))
  (is (= #{"JAVA_LISTENING_PORT" "FOO_VALUE"} @required-vars)))

(deftest init-verifies-presence-of-required-values
  (swap! required-vars conj "IMPORTANT_BUT_MISSING_VALUE")
  (is (thrown? AssertionError (init! "test/fixtures"))))
