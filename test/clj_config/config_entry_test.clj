(ns clj-config.config-entry-test
  (:require [clojure.test :refer :all]
            [clj-config.config-entry :refer :all]))

(deftest test-classify-definition
  (testing "Happy Path"
    (are [env definition classification]
        (= (classify-definition env definition)
           classification)

      :env "foo"                 :unvalidated
      :env ["foo" "something"]   :validated

      :app :bare-keyword                       :unvalidated
      :app [:some :key :path]                  :unvalidated
      :app [[:some :key :path] :anything-at-all] :validated))

  (testing "Invalid definition"
    (is (thrown? clojure.lang.ExceptionInfo (classify-definition :env 'symbol))
        "The classify-definition multimethod should throw an exception
         when it encounters a config definition it doesn't understand.")
    (let [data (ex-data (try (classify-definition :env 'symbol)
                             (catch clojure.lang.ExceptionInfo e e)))]
      (are [key expected-value]
          (= (get data key ::not-found) expected-value)

        :ns *ns*
        :env :env
        :definition 'symbol))))

(deftest test-read-config-def
  (testing "Happy Path"
    (are [env definition lookup-key validator]
        (let [result (read-config-def env definition)]
          (and (= validator (:validator result))
               (= lookup-key (:lookup-key result))))

      :env `["HOME" (constantly false)] "HOME" `(constantly false)
      :env `"HOME"                      "HOME" `(constantly true)

      :app `[[:some :key :path] (constantly false)]
            [:some :key :path] `(constantly false)

      :app `:bare-keyword :bare-keyword `(constantly true)
      :app `[:some :key :path]  [:some :key :path] `(constantly true))))
