(ns clj-config.config-entry)

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
   (let [value (get-in m (->vec k) ::not-found)]
     (if (= value ::not-found)
       (throw (ex-info (str "config var " k " not set") {}))
       value)))
  ([m k not-found]
   (when-not m (throw (ex-info "config not initialized" {})))
   (get-in m (->vec k) not-found)))

(defprotocol IValidate
  (-valid? [this value] "Return true if the value is valid, false otherwise."))

(defprotocol ILookup
  (-lookup [this config-data]))

(extend-protocol IValidate

  clojure.lang.IFn
  (-valid? [this value]
    (this value))

  java.util.regex.Pattern
  (-valid? [this value]
    (if (re-find this value)
      true
      false)))

(defrecord ConfigEntry
    [env lookup-key validator]

  IValidate
  (-valid? [this config-data]
    (->> (-lookup this config-data)
         (-valid? validator)))

  ILookup
  (-lookup [this config-data]
    (let [value (get-in* config-data lookup-key (:default this))]
      (if (= ::none value)
        (throw (ex-info (format "Config data not found: %s"
                                (pr-str (dissoc this :validator)))
                        this))
        value)))

  clojure.lang.Named
  (getName [_] lookup-key)
  (getNamespace [_] env))

(defmulti ->config-entry
  (fn [env config-entry options] env))

(def +default-validator+ (constantly true))
(defmethod ->config-entry :default
  [env lookup-key {:keys [default validator] :as options
                   :or {validator +default-validator+
                        default ::none}}]
  (assoc (->ConfigEntry env lookup-key validator)
         :default default))
