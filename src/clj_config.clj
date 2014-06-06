(ns clj-config
  (:require [clojure.java.io :as io]
            [clojure.string :refer [trim] :as s]
            [clojure.set :as set])
  (:import [java.util Properties]
           [java.io File]))

(defn trim-quotes
  [string]
  (s/replace string #"\"(.*)\"" "$1"))

(defn f->p
  "
  Turn a file name into a Properties object (which happens to be a Hashtable
  that also knows how to parse name=val, #comments).
  "
  [f]
  (let [file (io/as-file f)]
    (when (.exists file)
      (reduce (fn [acc [k v]] (assoc acc k (-> v trim trim-quotes)))
              {}
              (doto (Properties.) (.load (io/input-stream file)))))))

(defn get-env
  "
  Get a map of environment vars merged w/ config using the precedence:
  shell > .env
  "
  [root]
  (let [env-vars  (into {} (System/getenv))
        dotenv    (clojure.string/join File/separator (list root ".env"))
        dotenv-local (clojure.string/join File/separator (list root ".env.local"))]
    (merge (f->p dotenv) (f->p dotenv-local) env-vars)))

(defn get-var
  "Get a config var, barf if it doesn't exist or provide a default value."
  ([m k]
     (or (get m k)
         (throw (ex-info (str "config var " k " not set") {}))))
  ([m k not-found]
     (get m k not-found)))

(defn env [k] (throw (ex-info "config vars not initialized" {:var k})))
(defn config [] (throw (ex-info "config not initialized" {})))

(def ^:private required-vars (atom #{}))

(defn config-def
  [[name env-varname]]
  (assert (and (symbol? name) (string? env-varname)))
  `((swap! @(ns-resolve '~'clj-config '~'required-vars) conj ~env-varname)
    (def ~name (delay (env ~env-varname)))))

(defmacro defconfig
  [& names]
  (assert (even? (count names)))
  `(do ~@(mapcat config-def (partition 2 names))))

(defn init! []
  (let [config (get-env (or (get (System/getProperties) "jboss.server.config.dir")
                            (System/getenv "PWD")))]
    (assert (set/subset? @required-vars (set (keys config)))
            (format "Not all required configuration vars are defined. Missing vars: %s"
                    (pr-str (set/difference @required-vars (set (keys config))))))
    (alter-var-root #'env    (constantly (partial get-var config)))
    (alter-var-root #'config (constantly #(identity config)))))
