# profile

A Clojure library for profiling.

## Usage

This library is available on [Clojars](https://clojars.org/thunknyc.profile):

![Clojars Project](http://clojars.org/thunknyc.profile/latest-version.svg)

`profile` is a very much a work in progress. Feedback and contributions are
appreciated.

```clojure
(require '[profile.core :refer [profile
                                profile-defs
                                unprofile-defs
                                toggle-profile-def]])
(defn my-add [a b] (+ a b))
(defn my-mult [a b] (* a b))

(profile-defs my-add my-mult)

(profile
 (my-add (my-mult (rand-int 100000) (rand-int 1000000))
         (my-mult (rand-int 100000) (rand-int 1000000))))
```

Profile prints output using `pprint/print-table`; it looks like this:

```
|   :name | :n | :sum | :min | :max | :mad | :mean |
|---------+----+------+------+------+------+-------|
|  my-add |  1 |  2µs |  2µs |  2µs |  0µs |   2µs |
| my-mult |  2 |  6µs |  1µs |  5µs |  4µs |   3µs |

|    :stat | :value |
|----------+--------|
| :agg-sum |    8µs |
```


## License

Copyright © 2014 Edwin Watkeys

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
