(ns clj-config.test-helper
  (:require [clj-config :refer [system-get-env info]]))

(defn silencing-info [f]
  (with-redefs [info (constantly nil)]
    (f)))

(defn make-fake-get-env [env]
  (fn
    ([] env)
    ([k] (get env k))))

(defmacro with-fake-env [env & body]
  `(let [fake-get-env# (make-fake-get-env ~env)]
     (with-redefs [system-get-env fake-get-env#]
       ~@body)))

