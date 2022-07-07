# clj-config

## pre-commit

- Install: https://pre-commit.com/
- running locally: This will also happen automatically before committing to a branch, but you can also run the tasks with `pre-commit run --all-files`

Environmental configuration for Clojure projects inspired by
[the Ruby dotenv gem](https://github.com/bkeepers/dotenv).
`clj-config` loads config values in descending order of preference from

* environmental variables
* `.env.local`
* `.env`

`.env` and `.env.local` are files in the project's root folder of the
format

```shell
KEY="VALUE" # and a comment
```
**note** values *must* be quoted


Typically a project's repo will include `.env` files corresponding to
each non-prod environment, i.e. `.env.dev`, `.env.ci` and `.env.qa`.
For development, symlink an enviroment's config  to `.env` (`ln -s .env.foo .env`),
and override any values in `.env.local` (not stored in git).

## Usage

Config values should be declared using the `defconfig` macro, and
config should be initialized by calling `init!`, e.g.

```clojure
(require '[clj-config.core :refer [defconfig init!]])

(defconfig
  :env [[foo "BAZ"]
        [bar "QUX" {:default 42
                    :parser #(Integer/parseInt %)
                    :validator #(< 13 % 100)}]]
  :app [[sentry-cfg :sentry]                                   ; grab entire map
        [sentry-url [:sentry :dsn] {:validator #"^https?://"}] ; or just bits
        [sentry-usr [:sentry :usr]]])                          ; and pieces

(init!) ;; call init! once as the app starts

;; @foo contains the value of BAZ
```

`defconfig` creates delays `foo`, `bar`, `sentry-cfg`, `sentry-url`, and `sentry-usr`.
`init!` will raise an exception if an environmental var declared in `defconfig` isn't
present.

(for :app vars, exception is raised if the app-config edn structure does not
 contain the keypath specified in defconfig)

Each entry in the `defconfig` form can be supplied with an optional
map with the following keys:

| Key          | Type                    | Meaning                                                                   |
|--------------|-------------------------|---------------------------------------------------------------------------|
| `:default`   | arbitrary               | Will supply the var with this value if it's not found in the environment. |
| `:parser`    | function                | Converts the string found in the environment into the proper data.        |
| `:validator` | extender of `IValidate` | Examines the parsed data for correctness.                                 |

The `IValidate` protocol has already been extended to
`clojure.lang.IFn` and to `java.util.regex.Pattern`. These types can
be passed in the `:validator` out of the box. Functions used as
validators should return a boolean.

## Testing

Make a config fixture in your test namespace and call `(make-config-fixture ...)`

```clojure
(def +my-sample-config+
  {:rabbitmq
   {:exchange
    {:mail
     {:name "Mail"
      :queues
      {:incoming {:name "message-api.mail.Incoming" :routing-key "mail.incoming"}
       :outgoing {:name "message-api.mail.Outgoing" :routing-key "mail.outgoing"}}}}}})

(use-fixtures :once
  (make-config-fixture +my-sample-config+))
```

## app-config.edn

```clojure

{:sentry-dsn {:dev           nil
              [:ci :qa]      "ci/qa sentry dsn"
              #{:production} "prod sentry dsn"  ;; infra specifies 'production'
              :default       "default dsn"}

 :web-server-threads {:dev 80
                      :ci 40
                      :qa 20
                      :production 10
                      #{:default} 4}

 :api-key "invariant"

 :nested {:url "moarcats.gov"
          :pwd "m30w"
          :usr {[:dev :ci] "mittens-dev"    ;; vectors will be coerced to sets
                #{:qa}     "mittens-qa"
                :production "mittens-prod"
                [:default] "mittens-default"}}}

;; NOTE:
;; The current version of clj-config is hard-wired to recognize :default as a fall-back.
;; This bites, and will hopefully be made open to extensibility on the next go-round.

```
