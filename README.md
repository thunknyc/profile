# profile

A Clojure library for profiling.

## Usage

This library is available on [Clojars](https://clojars.org/thunknyc/profile):

![Clojars Project](http://clojars.org/thunknyc/profile/latest-version.svg)

The goal of this project is to work toward integration into CIDER, the
Clojure IDE for Emacs. The stats collected are inspired by
[`timber`](https://github.com/ptaoussanis/timbre).

After writing `profile`, I was puttering around CrossClj, I came
across Stuart Sierra's circa 2009 `core.contrib.profile` library. He
made some different decisions than I did in his implementation,
notably:

* Sierra's library is written to reduce to a nop unless an earmuffed
  var evaluates to true. This library always incurs profiling overhead
  for any profiled var.

* Sierra's library does not keep sample data but instead reduces the
  incoming sample data with each traced invocation. This library does,
  so that it can compute mean average deviation on an optionally
  rolling sample of the most recent invocations for a given var.

* Sierra's library allows arbitrary bodies of code to be
  profiled. This library only profiles functions bound to vars.

These differences are due in large part because `thunknyc/profile` is
intended to be used interactively in an IDE. Also, its API is modelled
on `clojure.org/tools.trace`, which focuses on tracing (and
un-tracing) vars.

```clojure
(require '[profile.core :refer :all])
(defn my-add [a b] (+ a b))
(defn my-mult [a b] (* a b))

(profile-vars my-add my-mult)

(profile {}
 (my-add (my-mult (rand-int 100000) (rand-int 1000000))
         (my-mult (rand-int 100000) (rand-int 1000000))))
```

`profile` prints output to `*err*` using `pprint/print-table`; it
looks like this:

```
|          :name | :n | :sum | :min | :max | :mad | :mean |
|----------------+----+------+------+------+------+-------|
|  #'user/my-add |  1 | 21µs | 21µs | 21µs |  0µs |  21µs |
| #'user/my-mult |  2 | 48µs |  3µs | 45µs | 42µs |  24µs |


|    :stat | :value |
|----------+--------|
| :agg-sum |   69µs |
```

## License

Copyright © 2014 Edwin Watkeys

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
