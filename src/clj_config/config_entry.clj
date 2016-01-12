(ns clj-config.config-entry)

(defprotocol IValidate
  (-valid? [this value] "Return true if the value is valid, false otherwise."))

(extend-type clojure.lang.IFn

  IValidate
  (-valid? [this value]
    (this value)))

(defmulti classify-definition
  (fn [env definition]
    [env (type definition)]))

(defmethod classify-definition :default
  [env definition]
  (throw (ex-info (format "Invalid config definition: %s env: %s ns:"
                          (pr-str definition) (pr-str env) (pr-str *ns*))
                  {:env env
                   :ns *ns*
                   :definition definition})))

(defmethod classify-definition [:env String]
  [_ _]
  :unvalidated)

(defmethod classify-definition [:env clojure.lang.IPersistentVector]
  [_ _]
  :validated)

(defmethod classify-definition [:app clojure.lang.IPersistentVector]
  [_ definition]
  (if (vector? (first definition))
    :validated
    :unvalidated))

(defmethod classify-definition [:app clojure.lang.Keyword]
  [_ _]
  :unvalidated)

(defmulti read-config-def
  (fn [env definition]
    [env (classify-definition env definition)]))

(defmethod read-config-def [:env :unvalidated]
  [_ definition]
  {:lookup-key definition
   :validator `(constantly true)})

(defmethod read-config-def [:env :validated]
  [_ definition]
  {:lookup-key (first definition)
   :validator  (second definition)})

(defmethod read-config-def [:app :unvalidated]
  [_ definition]
  {:lookup-key definition
   :validator `(constantly true)})

(defmethod read-config-def [:app :validated]
  [_ definition]
  {:lookup-key (first definition)
   :validator (second definition)})
