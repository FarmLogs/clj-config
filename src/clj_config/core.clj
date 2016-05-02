(ns clj-config.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :refer [trim] :as s]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [clj-config.app :as app]
            [clj-config.config-entry :as entry :refer [->config-entry]])
  (:import [java.util Properties]
           [java.io File]))

(def +env-files+ [".env" ".env.local"])

(defn trim-quotes
  [string]
  (s/replace string #"\"(.*)\"" "$1"))

(defn- f->p
  "
  Turn a file name into a Properties object (which happens to be a Hashtable
  that also knows how to parse name=val, #comments).
  "
  [f]
  (let [file (io/as-file f)]
    (if (.exists file)
      (do (log/info "Loading environment from:" f)
          (reduce (fn [acc [k v]] (assoc acc k (-> v trim trim-quotes)))
                  {}
                  (doto (Properties.) (.load (io/input-stream file)))))
      (log/debugf "Failed to load environment from '%s'. File does not exist." f))))

(defn make-path
  [root basename]
  (clojure.string/join File/separator (list root basename)))

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
  [[name env-varname opts]]
  `((let [entry# (->config-entry :env ~env-varname ~opts)]
      (swap! required-env conj entry#)
      (def ~name
        (reify clojure.lang.IDeref
          (deref [this#] (entry/-lookup entry# config)))))))

;;;;;;;; app config

(defn- ->set [value]
  (if (coll? value)
    (set value)
    #{value}))

(defn read-app-config [app-config-filename app-env]
  (when (and app-config-filename app-env)
    (log/info "Loading app-owned environment from:" app-config-filename)
    (-> app-config-filename
        slurp
        edn/read-string
        (app/prepare-config)
        (app/resolve-app-config (keyword app-env)))))

(def app-config nil)
(def required-app-config (atom #{}))

(defn app-config-def
  [[name env-varname opts]]
  (assert (and (symbol? name) (every? keyword? (entry/->vec env-varname))))
  `((let [config-entry# (->config-entry :app ~env-varname ~opts)]
      (swap! required-app-config conj config-entry#)
      (def ~name
        (reify clojure.lang.IDeref
          (deref [this#] (entry/-lookup config-entry# app-config)))))))

;;;;;;;;;;;;;;;;;;;;
;;
;; problems:
;; - still duplication
;;
;;;;;;;;;;;;;;;;;;;;

(defn valid-app-config?
  [app-config-entries actual-config]
  (let [required-names (->> (remove entry/has-default? app-config-entries)
                            (map :lookup-key)
                            (into #{}))]
    (assert (every? (partial app/contains-keypath? actual-config) required-names)
            (format "Not all required APP configuration vars are defined. Missing vars: %s"
                    (pr-str (remove (partial app/contains-keypath?
                                             actual-config)
                                    required-names))))
    (assert (every? #(entry/-valid? % actual-config) app-config-entries)
            (format "Not all required APP configuration vars are valid: %s"
                    (->> (remove #(entry/-valid? % actual-config) app-config-entries)
                         (map :lookup-key)
                         (interpose ", ")
                         (apply str))))
    true))

(defn init-app-config!
  ([env] (init-app-config! env (System/getProperty "user.dir")))
  ([env root-dir]
   (let [app-config-file (when (seq @required-app-config)
                           (entry/get-in* env "CLJ_APP_CONFIG"
                                          {:default
                                           (str root-dir (System/getProperty "file.separator") "config.edn")}))
         app-environment (entry/get-in* env "APPLICATION_ENVIRONMENT" {:default "dev"})
         app-config (read-app-config app-config-file app-environment)]
     (when (valid-app-config? @required-app-config app-config)
       (alter-var-root #'app-config (constantly app-config))))))


(defn valid-env-config?
  [env-config-entries actual-config]
  (let [required-names (->> (remove entry/has-default? env-config-entries)
                            (map :lookup-key)
                            (into #{}))]
    (assert (set/subset? required-names (set (keys actual-config)))
            (format "Not all required ENV configuration vars are defined. Missing vars: %s"
                    (pr-str (set/difference required-names (set (keys actual-config))))))
    (assert (every? #(entry/-valid? % actual-config) env-config-entries)
            (format "Not all required ENV configuration vars are valid: %s"
                    (->> (remove #(entry/-valid? % actual-config) env-config-entries)
                         (map :lookup-key)
                         (interpose ", ")
                         (apply str))))
    true))

(defn init!
  ([]
   (init! (or (get (System/getProperties) "jboss.server.config.dir")
              (System/getProperty "user.dir"))))
  ([root-dir]
   (let [config (read-env root-dir)]
     (when (valid-env-config? @required-env config)
       (alter-var-root #'config (constantly config))
       (init-app-config! config root-dir)))))

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
  (let [config-map (reduce-kv
                    (fn [config env config-map]
                      (assoc config
                             env
                             (if (symbol? config-map)
                               (if-let [v (ns-resolve *ns* config-map)]
                                 (let [dv (deref v)]
                                   (if (nil? dv)
                                     (throw (ex-info "nil config spec"
                                                     {:symbol config-map
                                                      :config-type env
                                                      :ns *ns*}))
                                     dv))
                                 (throw (ex-info "error resolving symbol"
                                                 {:symbol config-map
                                                  :config-type env
                                                  :ns *ns*})))
                               config-map)))
                    {}
                    m)
        {:keys [app env]} config-map]
    `(do
       ~@(mapcat app-config-def app)
       ~@(mapcat env-config-def env))))

(comment
  (require '[clj-config.core :refer [defconfig]])

  (defconfig
    :app [[sentry-url :sentry-dsn]]
    :env [[oracle-url "DATASTORES_ORACLE_WEBICON_HOSTNAME"]]))
