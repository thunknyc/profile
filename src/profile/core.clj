(ns profile.core
"A Clojure library for profiling.

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
"
  (:require [clojure.pprint :refer [print-table]]))

(def ^:private default-max-sample-count 50000)

(defn profile-session
  "Inititalize profile session with optional maximum sample
  count. Default maximum sample count is 10,000."
  ([max-sample-count]
     (atom (with-meta {} {::max-sample-count max-sample-count})))
  ([] (profile-session default-max-sample-count)))

(def ^:dynamic *profile-data* (profile-session))

(defmacro with-session
  "Evaluate `BODY` in context of a new profile sassion initializaed
  with `OPTIONS`, a map that may contain a `:max-sample-count`."
  [OPTIONS & BODY]
  `(binding [*profile-data* (profile-session (:max-sample-count ~OPTIONS))]
     ~@BODY))

(defn clear-profile-data []
  (reset! *profile-data* {}))

(defn max-sample-count
  "Return maximum sample count of current profile session."
  []
  (::max-sample-count (meta (deref *profile-data*))))

(defn set-max-sample-count
  "Set maximum sample count of current profile session. Maximum sample
  count refers to the the maximum number of samples any individual
  name may be associated with. Value is applied when time is accured;
  this call will not truncate any profile data."
  [n]
  (swap! *profile-data* with-meta {::max-sample-count n})
  n)



(defn ^:private truncate-samples
  [samples max-sample-count]
  (if-let [sample-count (count samples)]
    (if (and max-sample-count (> (inc sample-count) max-sample-count))
      (subvec samples (- sample-count max-sample-count -1))
      samples)))

(defn ^:private accrue-time
  [session-atom name nanos]
  (swap! session-atom
         (fn [session]
           (let [samples
                 (truncate-samples (get session name []) (max-sample-count))]
             (assoc-in session [name] (conj samples nanos))))))

(defn ^:no-doc profile-fn*
  "This function is exported only so the profile-var macro can make
  use of it."
  [session f var]
  (with-meta (fn [& args]
               (let [nano-now (System/nanoTime)
                     val (apply f args)
                     elapsed (- (System/nanoTime) nano-now)]
                 (accrue-time (deref session) var elapsed)
                 val))
    {::profiled (deref var)}))

(defn profiled?
  "Reurns a truthy value if `f` is currently profiled."
  [f]
  (::profiled (meta f)))

(defmacro profile-var
  "If `VAR` is not already profiled, wraps the associated value with a
  function that accrues time to the current profile session."
  [VAR]
  `(if-let [f# (profiled? ~VAR)]
     f#
     (alter-var-root (var ~VAR)
                     #(profile-fn* (var *profile-data*) % (var ~VAR)))))

(defmacro profile-vars
  "Equivalent to evaluating `profile-var` on each element of `VARS`."
  [& VARS]
  (let [FORMS (for [VAR VARS] `(profile-var ~VAR))]
    `(do ~@FORMS)))

(defmacro unprofile-var
  "If `VAR` is profiled, replaces binding with original function."
  [VAR]
  `(when-let [f# (profiled? ~VAR)]
     (alter-var-root (var ~VAR) (fn [_#] f#))))

(defmacro unprofile-vars
  "Equivalent to evaluating `unprofile-var` on each element of
  `VARS`."
  [& VARS]
  `(doseq [VAR# ~VARS] (profile-var VAR#)))

(defn toggle-profile-var*
  "For use by cider-nrepl."
  [v]
  (if-let [f (profiled? @v)]
    (do (alter-var-root v (fn [_] f))
        false)
    (do (alter-var-root v #(profile-fn* (var *profile-data*) % v))
        true)))

(defmacro toggle-profile-var
  "Profiles or unprofiles `VAR` depending on its current
  state. Returns a truthy value if `VAR` is profiled subsequent to
  evaluation of this macro."
  [VAR]
  `(toggle-profile-var* (var ~VAR)))



(defn ^:private entry-stats
  [entry]
  (let [[name xs] entry
        n (count xs)
        middle (int (/ n 2))
        q1-index (int (* n 0.25))
        q3-index (max 0 (dec (int (* n 0.75))))
        sum (reduce + 0 xs)
        xs-sorted (vec (sort xs))
        median (get xs-sorted middle)
        mean (double (/ sum n))
        mad (get (vec (sort (map #(Math/abs (- median %)) xs))) middle)
        variance (/ (reduce + 0 (map #(Math/pow (- mean %) 2.0) xs)) n)]
    {:name name
     :n n
     :sum sum
     :min (apply min xs)
     :max (apply max xs)
     :mean mean
     :med median
     :mad mad
     :q1 (get xs-sorted q1-index)
     :q3 (get xs-sorted q3-index)
     :sd (Math/sqrt variance)
     :xs xs}))

(defn ^:private aggregate-stats
  [stats]
  (reduce (fn [agg-stats stat]
            (-> agg-stats
                (update-in [:agg-sum] (fnil + 0) (:sum stat))
                ;; Structured for future expansion.
                ))
          {}
          stats))

(defn summary
  "Returns a map containing two keys, `:stats` and `:agg-stats`. The
  former containts a sequence of maps containing statistics describing
  the profile data for each profiled name. `:agg-stats` contains a map
  of statistics relevant to the aggregate of all profiles names."
  ([] (summary *profile-data*))
  ([session]
     (let [state @session
           stats (map entry-stats state)
           agg-stats (aggregate-stats stats)]
       {:agg-stats agg-stats :stats stats})))

(defn ^:private format-int
  [n]
  (format "%,d" n))

(defn ^:private format-nanoseconds
  [nanos]
  (cond (> nanos 1000000000)
        (format "%.1fs" (/ nanos 1.0E9))
        (> nanos 1000000)
        (format "%.0fms" (/ nanos 1.0E6))
        :else
        (format "%.0fµs" (/ nanos 1.0E3))))

(defn ^:private format-stats
  [stats]
  (-> stats
      (update-in [:n] format-int)
      (update-in [:sum] format-nanoseconds)
      (update-in [:min] format-nanoseconds)
      (update-in [:max] format-nanoseconds)
      (update-in [:mad] format-nanoseconds)
      (update-in [:q1] format-nanoseconds)
      (update-in [:med] format-nanoseconds)
      (update-in [:q3] format-nanoseconds)
      (update-in [:mean] format-nanoseconds)
      (update-in [:sd] format-nanoseconds)))

(defn ^:private format-agg-stats
  [agg-stats]
  (-> agg-stats
      (update-in [:agg-sum] (fnil format-nanoseconds 0))))

(defn ^:private tableify-agg-stats
  [agg-stats]
  (for [[k v] (format-agg-stats agg-stats)]
    {:stat k :value v}))

(defn print-summary
  "Prints to *err* individual and aggregate statistics for profiled
  names. "
  ([] (print-summary *profile-data*))
  ([session]
     (binding [*out* *err*])
     (let [{:keys [stats]} (summary session)
           formatted-stats (map format-stats stats)]
       (clojure.pprint/print-table
        [:name :n :sum :q1 :med :q3 :sd :mad]
        formatted-stats))))

(defmacro profile
  "Execute BODY in a new profile session using `OPTIONS` and print
  summary of collected profile data to `*err*` using `print-summary`."
  [OPTIONS & BODY]
  `(with-session ~OPTIONS
     (let [val# (do ~@BODY)]
       (print-summary)
       val#)))
