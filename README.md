# clj-config

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
(require '[clj-config :refer [defconfig init!]])

(defconfig
  foo "BAZ"
  bar "QUX")

(init!)

;; @foo contains the value of BAZ
```

`defconfig` creates delays `foo` and `bar`. `init!` will raise an
exception if an environmental var declared in `defconfig` isn't
present.
