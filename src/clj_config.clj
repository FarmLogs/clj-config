(ns clj-config ;; TODO: no single-segment ns
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :refer [trim] :as s]
            [clojure.set :as set]
            [clj-config.app :as app])
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

(defn ->vec
  [x]
  (if (sequential? x)
    x
    (vector x)))

(defn get-in*
  "Get k from m, barf if not found (unless provided a default value).
   Wrap k in a vector if k is not already
   "
  ([m k]
   (when-not m (throw (ex-info "config not initialized" {})))
   (or (get-in m (->vec k))
       (throw (ex-info (str "config var " k " not set") {}))))
  ([m k not-found]
   (when-not m (throw (ex-info "config not initialized" {})))
   (get-in m (->vec k) not-found)))

(defn system-get-env
  ([] (System/getenv))
  ([key] (System/getenv key)))

;;;;;;;;; Infra/local env stuff

(defn read-env
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

(def config nil)
(def required-env (atom #{}))

(defn env-config-def
  [[name env-varname]]
  (assert (and (symbol? name) (string? env-varname)))
  `((swap! required-env conj ~env-varname)
    (def ~name (delay (get-in* config ~env-varname)))))

;;;;;;;; app config

(defn- ->set [value]
  (if (coll? value)
    (set value)
    #{value}))

(defn read-app-config [app-config-filename app-env]
  (when (and app-config-filename app-env)
    (info "Loading app-owned environment from:" app-config-filename)
    (-> app-config-filename
        slurp
        edn/read-string
        (app/prepare-config)
        (app/resolve-app-config (keyword app-env)))))

(def app-config nil)
(def required-app-config (atom #{}))

(defn app-config-def
  [[name env-varname]]
  (assert (and (symbol? name) (every? keyword? (->vec env-varname))))
  `((swap! required-app-config conj ~env-varname)
    (def ~name (delay (get-in* app-config ~env-varname)))))

;;;;;;;;;;;;;;;;;;;;
;;
;; problems:
;; - still duplication
;;
;;;;;;;;;;;;;;;;;;;;

(defn init-app-config! [env]
  (let [app-config-file (when (seq @required-app-config)
                          (get-in* env "CLJ_APP_CONFIG"))
        app-environment (get-in* env "APPLICATION_ENVIRONMENT" "dev")
        app-config (read-app-config app-config-file app-environment)]
    (assert (every? (partial app/contains-keypath? app-config) @required-app-config)
            (format "Not all required APP configuration vars are defined. Missing vars: %s"
                    (pr-str (set/difference @required-app-config (set (keys app-config))))))
    (alter-var-root #'app-config (constantly app-config))))

(defn init!
  ([]
   (init! (or (get (System/getProperties) "jboss.server.config.dir")
              (system-get-env "PWD"))))
  ([root-dir]
   (let [config (read-env root-dir)]
     (assert (set/subset? @required-env (set (keys config)))
             (format "Not all required ENV configuration vars are defined. Missing vars: %s"
                     (pr-str (set/difference @required-env (set (keys config))))))
     (alter-var-root #'config (constantly config))

     (init-app-config! config))))

(defmacro defconfig
  "
   Usage:
   (defconfig
    :app {sentry-url :sentry-dsn}
    :env {oracle-url \"DATASTORES_ORACLE_WEBICON_HOSTNAME\"})

   see README.md for determining whether your new configuration variable
   falls under :env (infra-owned) or :app (IDG-owned)
   "
  [& {:keys [app env] :as m}]
  `(do
     ~@(mapcat app-config-def app)
     ~@(mapcat env-config-def env)))

(comment
  (require '[clj-config :refer [defconfig]])

  (defconfig
    :app {sentry-url :sentry-dsn}
    :env {oracle-url "DATASTORES_ORACLE_WEBICON_HOSTNAME"}))
