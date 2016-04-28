(ns clj-config.config-entry-test
  (:require [clojure.test :refer :all]
            [clj-config.config-entry :refer :all]))

(deftest test-config-entry-ctor
  (let [config-data {"HOME" "foo"
                     "NUMBER" "129"
                     :some {:key {:path "bar"}
                            :other {:key 1337}}}]
    (testing "Happy Path"
      (are [env definition config-value]
          (let [[entry options] definition
                config-entry (->config-entry env entry options)]
            (and (-valid? config-entry config-data)
                 (= (-lookup config-entry config-data)
                    config-value)))

        :env ["NUMBER" {:parser #(Integer/parseInt %)}] 129
        :app [[:some :other :key] {:parser str}] "1337"

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

        :env "NUMBER"       {:validator integer?}
        :env "DOESNT_EXIST" {:validator string?
                             :default 42}

        :app [:some]        {:validator string?}))

    (testing "Validators run after parsers"
      (are [env lookup-key opts]
          (-valid? (->config-entry env lookup-key opts)
                   config-data)

        :env "NUMBER"       {:validator integer?
                             :parser #(Integer/parseInt %)}))))
