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

(defn get*
  "Get k from m, barf if not found (unless provided a default value)."
  ([m k]
   (when-not m (throw (ex-info "config not initialized" {})))
   (or (get m k)
       (throw (ex-info (str "config var " k " not set") {}))))
  ([m k not-found]
   (when-not m (throw (ex-info "config not initialized" {})))
   (get m k not-found)))

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
    (def ~name (delay (get* config ~env-varname)))))

;;;;;;;; app config

(defn- ->set [value]
  (if (coll? value)
    (set value)
    #{value}))

(defn transform-app-config [app-config env]
  (reduce-kv (fn [acc k value-map]
               (if-not (map? value-map)
                 (assoc acc k value-map)
                 (let [[_ value]
                       (first (filter (fn [[env-key _]] (contains? (->set env-key) env))
                                      value-map))]
                   (assoc acc k value))))
             {}
             app-config))

(defn read-app-config [app-config-filename app-env]
  (when (and app-config-filename app-env)
    (info "Loading app-owned environment from:" app-config-filename)
    (-> app-config-filename
        slurp
        edn/read-string
        (transform-app-config (keyword app-env)))))

(def app-config nil)
(def required-app-config (atom #{}))

(defn app-config-def
  [[name env-varname]]
  (assert (and (symbol? name) (keyword? env-varname)))
  `((swap! required-app-config conj ~env-varname)
    (def ~name (delay (get* app-config ~env-varname)))))

;;;;;;;;;;;;;;;;;;;;
;;
;; problems:
;; - still duplication
;;
;;;;;;;;;;;;;;;;;;;;

(defn init-app-config! [application-environment]
  (let [app-config (read-app-config (system-get-env "APP_OWNED_CONFIG_EDN_FILE")
                                   application-environment)]
    (assert (set/subset? @required-app-config (set (keys app-config)))
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

     (init-app-config! (get* config "APPLICATION_ENVIRONMENT" "dev")))))



(defmacro defenv
  "
   Usage:
   (defenv
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
  (require '[clj-config :refer [defenv]])

  (defenv
    :app {sentry-url :sentry-dsn}
    :env {oracle-url "DATASTORES_ORACLE_WEBICON_HOSTNAME"}))
