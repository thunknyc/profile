# profile

A library for profiling Clojure code, particularly useful for profiling non-pure code.

## Usage

This library is available on
[Clojars](https://clojars.org/thunknyc/profile); the Leiningen
dependency for the most recently released version is:

![Clojars Project](http://clojars.org/thunknyc/profile/latest-version.svg)

If you use CIDER in Emacs, check out
[nrepl-profile](http://github.com/thunknyc/nrepl-profile), a project
that provides nREPL middleware that takes advantage of this
project. (The Emacs portions of the nrepl-profile are available in
MELPA as [`cider-profile`](http://melpa.org/#/cider-profile).

## Introduction

The goal of this project is to build a profiling tool that can be used
on non-pure (and pure) code, particularly code that interacts with 
external systems where the function calls may not be idempotent or 
repeatable. Profile is being built with the plan of integration into CIDER, the
Clojure IDE for Emacs. The stats collected are:

* `:n` Number of samples.
* `:sum` Aggregate time spent in fn.
* `:q1` First quartile i.e. twenty-fifth percentile.
* `:med` Median i.e. fiftieth percentile.
* `:q3` Third quartile i.e. seventy-fifth percentile.
* `:sd` Standard deviation i.e. the square root of the sum of squares
  of differences from the mean.
* `:mad` Mean average deviation, I don't feel like looking up the
  definition just now.

Full [API documentation](http://thunknyc.github.io/profile/) is available.

After writing `profile`, I was puttering around CrossClj, I came
across Stuart Sierra's circa 2009 `core.contrib.profile` library. He
made some different decisions in his implementation than I did,
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

People have mentioned Criterium. Criterium is great! Definitely use
it if it works for you. That said, it doesn't work well for
non-idempotent, non-referentially transparent profiling. I wrote this
library primarily to assist in understanding the performance
characteristics of code that is highly dependent on interactions
outside the JVM, for example due to HTTP requests, database queries,
sending pub-sub messages, e.t.c.

## Example

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
|          :name | :n | :sum | :q1 | :med | :q3 | :sd | :mad |
|----------------+----+------+-----+------+-----+-----+------|
|  #'user/my-add |  1 |  2µs | 2µs |  2µs | 2µs | 0µs |  0µs |
| #'user/my-mult |  2 | 11µs | 3µs |  8µs | 3µs | 3µs |  5µs |


|    :stat | :value |
|----------+--------|
| :agg-sum |   13µs |
```

## License

Copyright © 2014 Edwin Watkeys

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
