(ns clj-config.app
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :refer [trim] :as s]
            [clojure.set :as set]
            [clojure.zip :as z])
  (:import [java.util Properties]
           [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities
(defn map-zipper
  "courtesy of cgrand:
   http://stackoverflow.com/a/15020649"
  [m]
  (z/zipper (fn mzip-branch
              [x]
              (or (map? x) (map? (nth x 1))))

            (fn mzip-children
              [x]
              (seq (if (map? x) x (nth x 1))))

            (fn mzip-make-node
              [x children]
              (if (map? x) 
                (into {} children) 
                (assoc x 1 (into {} children))))
            m))

(defn ->vec
  [x]
  (cond (nil?    x) []
        (vector? x)  x
        (coll?   x) (vec x)
        :otherwise  [x]))

(defn set-val!
  "edits the val at the current node of the map-zipper"
  [loc val]
  (z/edit loc (juxt key (constantly val))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Used when validating that app-config contains all required vars  
(defn keys-in
  "courtesy of A.Webb:
   http://stackoverflow.com/a/21770247"
  [m] 
  (letfn [(branch? [[path m]] (map? m)) 
          (children [[path m]] (for [[k v] m] [(conj path k) v]))] 
    (let [key-paths (if (empty? m) 
                      []
                      (loop [t (z/zipper branch? children nil [[] m]), paths []] 
                        (cond (z/end? t) paths 
                              (z/branch? t) (recur (z/next t), paths) 
                              :leaf (recur (z/next t), (conj paths (first (z/node t)))))))]

      (reduce (fn [c x]
                (into c (rest (reductions conj [] x))))  ; and Colin Jones
              #{}
              key-paths))))

(defn contains-keypath?
  [m ks]
  (->> (get-in m (->vec ks) ::not-found)
       (not= ::not-found)))


(defn resolve-app-config
  "Walks a config.edn map structure, resolving the env.

    Inputs:
    opt - map containing
    :envs      - PersistentSet of valid environments
    :default   - keyword indicating default (in case the current env is not represented at a given choice point)
    
    env    - the current application environment
    config - an edn map stucture representing application config,
             possibly containing multiple options for a provided key.

    Example:
             {:sentry-dsn {[:ci :dev] \"\"
                            :qa       \"qa-url.sentry.com\"
                            #{:production}  \"prod-url.sentry.com\"}}

    Output: a modified config map where options are resolved for the current env
             {:sentry-dsn \"qa-url.sentry.com\"}  ; ready for QA!

    "
  [{:keys [envs default] :as opt} env config]
  (let [walk (fn walker
               [z]
               (if (z/end? z)
                 (z/root z)
                 (if-let [kids (and (z/branch? z) (z/children z))]
                   (if (some (some-fn envs coll?) (map first kids))
                     (let [kids-map (reduce (fn [c kv]
                                              (into c (for [i (->vec (first kv))]
                                                        {i (second kv)})))
                                            {} kids)]
                           (if (contains? kids-map env)
                             (recur (z/next (set-val! z (get kids-map env)))) ;good
                             (if (contains? kids-map default)
                               (recur (z/next (set-val! z (get kids-map default))))
                               (throw (ex-info (str "missing env: (" env ") "
                                                    "at node: " (z/node z) "\n"
                                                    "with no ::default specified.")
                                               {:env env :envs (keys kids-map)})))))
                       (recur (z/next z)))
                     (recur (z/next z)))))]
        (-> (map-zipper config)
            z/next
            walk)))
