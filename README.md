# profile

A Clojure library for profiling.

## Usage

This library is available on [Clojars](https://clojars.org/thunknyc/profile):

![Clojars Project](http://clojars.org/thunknyc/profile/latest-version.svg)

`profile` is a very much a work in progress. Feedback and
contributions are appreciated. The goal of this project is to work
toward integration into CIDER, the Clojure IDE for Emacs. The stats
collected are inspired by
[`timber`](https://github.com/ptaoussanis/timbre).

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
