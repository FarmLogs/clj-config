(ns clj-config.config-entry-test
  (:require [clojure.test :refer :all]
            [clj-config.config-entry :refer :all]))

(deftest test-config-entry-ctor
  (let [config-data {"HOME" "foo"
                     "NUMBER" 129
                     :some {:key {:path "bar"}}}]
    (testing "Happy Path"
      (are [env definition config-value]
          (let [[entry options] definition
                config-entry (->config-entry env entry options)]
            (and (-valid? config-entry config-data)
                 (= (-lookup config-entry config-data)
                    config-value)))

        :env ["HOME" {:validator string?}] "foo"
        :env ["HOME"]                      "foo"
        :env ["HOME" {:default "bar"}]     "foo"

        :env ["DOESNT_EXIST" {:default 42}]   42
        :app [[:doesnt :exist] {:default 42}] 42

        :app [[:some :key :path] {:validator string?}] "bar"
        :app [[:some :key :path]]                      "bar"
        :app [[:some :key :path] {:default "foo"}]     "bar"
        :app [:some]                                   (:some config-data)))

    (testing "Config value doesn't exist"
      (let [config-entry (->config-entry :env "DOESNT_EXIST" nil)]
        (is (thrown? clojure.lang.ExceptionInfo (-lookup config-entry config-data)))
        (is (thrown? clojure.lang.ExceptionInfo (-valid? config-entry config-data)))))

    (testing "Invalid config value"
      (are [env lookup-key opts]
          (not (-valid? (->config-entry env lookup-key opts)
                        config-data))

        :env "NUMBER"       {:validator string?}
        :env "DOESNT_EXIST" {:validator string?
                             :default 42}

        :app [:some]        {:validator string?}
        :app [:doesnt-exist] {:validator string?
                              :default 42}))))
