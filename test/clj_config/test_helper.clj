(ns clj-config.test-helper
  (:require [clj-config.core :refer [system-get-env]]
            [clojure.tools.logging :as log]))

(defn silencing-logging [f]
  (with-redefs [log/log* (constantly nil)]
    (f)))

(defn make-fake-get-env [env]
  (fn
    ([] env)
    ([k] (get env k))))

(defmacro with-fake-env [env & body]
  `(let [fake-get-env# (make-fake-get-env ~env)]
     (with-redefs [system-get-env fake-get-env#]
       ~@body)))

