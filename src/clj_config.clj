(ns clj-config ;; TODO: no single-segment ns
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :refer [trim] :as s]
            [clojure.set :as set])
  (:import [java.util Properties]
           [java.io File]))

(def +env-files+ [".env" ".env.local"])

(defn info [& args]
  (apply println args))

(defn error [& args]
  (apply println args))

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
    (if (.exists file)
      (do (info "Loading environment from:" f)
          (reduce (fn [acc [k v]] (assoc acc k (-> v trim trim-quotes)))
                  {}
                  (doto (Properties.) (.load (io/input-stream file)))))
      (error (format "Failed to load environment from '%s'. File does not exist." f)))))

(defn make-path
  [root basename]
  (clojure.string/join File/separator (list root basename)))

(defn get-var
  "Get a config var, barf if it doesn't exist or provide a default value."
  ([m k]
     (or (get m k)
         (throw (ex-info (str "config var " k " not set") {}))))
  ([m k not-found]
     (get m k not-found)))

(defn system-get-env
  ([] (System/getenv))
  ([key] (System/getenv key)))

;;;;;;;;; Infra/local env stuff

(defn get-env
  "
  Get a map of environment vars merged w/ config using the precedence:
  environment variables beat
  ENV_FILE beats .env/.env.local (mutually exclusive)
  "
  [root]
  (let [env-vars   (into {} (system-get-env))
        env-filenames (if-let [env-file (system-get-env "ENV_FILE")]
                        [env-file]
                        (for [name +env-files+] (make-path root name)))]
    (apply merge (conj (mapv f->p env-filenames) env-vars))))

(defn env [k] (throw (ex-info "config vars not initialized" {:var k})))
(defn config [] (throw (ex-info "config not initialized" {})))

(def required-vars (atom #{}))

(defn config-def
  [[name env-varname]]
  (assert (and (symbol? name) (string? env-varname)))
  `((swap! @(ns-resolve '~'clj-config '~'required-vars) conj ~env-varname)
    (def ~name (delay (env ~env-varname)))))

(defmacro defconfig
  [& names]
  {:pre [(even? (count names))]}
  `(do ~@(mapcat config-def (partition 2 names))))

;;;;;;;; app config

(defn- ->set [value]
  (if (coll? value)
    (set value)
    #{value}))

(defn transform-app-config [input-app-config this-env]
  (reduce-kv (fn [acc k value-map]
               (if-not (map? value-map)
                 (assoc acc k value-map)
                 (let [[_ value]
                       (first (filter (fn [[env-key _]] (contains? (->set env-key) this-env))
                                      value-map))]
                   (assoc acc k value))))
             {}
             input-app-config))

(defn get-app-config [app-config-filename app-env]
  (when (and app-config-filename app-env)
    (info "Loading app-owned environment from:" app-config-filename)
    (-> app-config-filename
        slurp
        edn/read-string
        (transform-app-config (keyword app-env)))))

(defn app-env [k] (throw (ex-info "app config vars not initialized" {:var k})))
(defn app-config [] (throw (ex-info "app config not initialized" {})))

(def required-app-config-vars (atom #{}))

(defn app-config-def
  [[name env-varname]]
  (assert (and (symbol? name) (keyword? env-varname)))
  `((swap! @(ns-resolve '~'clj-config '~'required-app-config-vars) conj ~env-varname)
    (def ~name (delay (app-env ~env-varname))))) ;; should be app-env

(defmacro defappconfig
  [& names]
  {:pre [(even? (count names))]}
  `(do ~@(mapcat app-config-def (partition 2 names))))

;;;;;;;;;;;;;;;;;;;;
;;
;; problems:
;; - duplication all over the place
;; - env & config still have multiple meanings
;;
;;;;;;;;;;;;;;;;;;;;

(defn init-app-config! [application-environment]
  (let [app-config (get-app-config (system-get-env "APP_OWNED_CONFIG_EDN_FILE")
                                   application-environment)]
    (assert (set/subset? @required-app-config-vars (set (keys app-config)))
            (format "Not all required configuration vars are defined. Missing vars: %s"
                    (pr-str (set/difference @required-app-config-vars (set (keys app-config))))))
    ;; app-env: lookup fn into config
    (alter-var-root #'app-env    (constantly (partial get-var app-config)))
    ;; app-config: map of config values
    (alter-var-root #'app-config (constantly #(identity app-config)))))

(defn init!
  ([]
   (init! (or (get (System/getProperties) "jboss.server.config.dir")
              (system-get-env "PWD"))))
  ([root-dir]
   (let [config (get-env root-dir)]
     (assert (set/subset? @required-vars (set (keys config)))
             (format "Not all required configuration vars are defined. Missing vars: %s"
                     (pr-str (set/difference @required-vars (set (keys config))))))
     ;; env: lookup fn into config
     (alter-var-root #'env    (constantly (partial get-var config)))
     ;; config: map of config values
     (alter-var-root #'config (constantly #(identity config)))

     (init-app-config! (get-var config "APPLICATION_ENVIRONMENT" "dev")))))

