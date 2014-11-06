(ns profile.core)

(defrecord ProfileSession [state])

(defn profile-session []
  (->ProfileSession (atom {})))

(def ^:dynamic *current-session* (profile-session))

(defmacro with-session [& BODY]
  `(binding [*current-session* (profile-session)]
     ~@BODY))

(defn clear-current-session []
  (reset! (:state *current-session*) {}))



(defn accrue-time
  [session name nanos]
  (swap! (:state @session)
         #(update-in % [name] (fnil conj []) nanos)))

(defn profile-fn
  [session f name]
  (fn [& args]
    (let [nano-now (System/nanoTime)
          val (apply f args)
          elapsed (- (System/nanoTime) nano-now)]
      (accrue-time session name elapsed)
      val)))

(defmacro p
  "Evaluate `BODY` and accrue time to key `NAME` in current profiling
  session. "
  [NAME & BODY]
  `((profile-fn #'*current-session*
                (fn []
                  ~@BODY)
                (quote ~NAME))))

(defmacro depfn
  "Define a function "
  [NAME DOCSTRING? ARGS & BODY]
  (if (string? DOCSTRING?)
    `(defn ~NAME ~DOCSTRING? ~ARGS (p ~NAME ~@BODY))
    (let [BODY (cons ARGS BODY)
          ARGS DOCSTRING?]
      `(defn ~NAME ~ARGS (p ~NAME ~@BODY)))))

(defn profiled? [f]
  (not (not (:profiled-fn (meta f)))))

(defmacro profile-def [NAME]
  `(if (profiled? ~NAME)
     true
     (do (alter-var-root #'~NAME
                         #(with-meta
                            (profile-fn #'*current-session* % (quote ~NAME))
                            {:profiled-fn ~NAME}))
         true)))

(defmacro profile-defs [& NAMES]
  (let [FORMS (for [NAME NAMES] `(profile-def ~NAME))]
    `(do ~@FORMS)))

(defmacro unprofile-def [NAME]
  `(if-let [f# (:profiled-fn (meta ~NAME))]
     (alter-var-root #'~NAME (fn [_# x#] x#) f#)))

(defmacro unprofile-defs [& NAMES]
  (let [FORMS (for [NAME NAMES] `(profile-def ~NAME))]
    `(do ~@FORMS)))

(defmacro toggle-profile-def [NAME]
  `(if-let [f# (:profiled-fn (meta ~NAME))]
     (do (alter-var-root #'~NAME
                         (fn [_# x#] x#) f#)
         nil)
     (do (alter-var-root #'~NAME
                         #(with-meta
                            (profile-fn #'*current-session* % (quote ~NAME))
                            {:profiled-fn ~NAME}))
         true)))

(defn entry-stats [entry]
  (let [[name xs] entry
        n (count xs)
        middle (int (/ n 2))
        sum (reduce + 0 xs)
        median (get (vec (sort xs)) middle)
        mad (get (vec (sort (map #(Math/abs (- median %)) xs))) middle)]
    {:name name
     :n n
     :sum sum
     :min (apply min xs)
     :max (apply max xs)
     :mean (double (/ sum n))
     :median median
     :mad mad
     :xs xs}))

(defn aggregate-stats [stats]
  (reduce (fn [agg-stats stat]
            (-> agg-stats
                (update-in [:agg-sum] (fnil + 0) (:sum stat))
                ;; Structured for future expansion.
                ))
          {}
          stats))

(defn summary
  ([] (summary *current-session*))
  ([session]
     (let [state @(:state session)
           stats (map entry-stats state)
           agg-stats (aggregate-stats stats)]
       {:agg-stats agg-stats :stats stats})))

(defn format-int
  [n]
  (format "%,d" n))

(defn format-nanoseconds
  [nanos]
  (cond (> nanos 1000000000)
        (format "%.1fs" (/ nanos 1.0E9))
        (> nanos 1000000)
        (format "%.0fms" (/ nanos 1.0E6))
        :else
        (format "%.0fµs" (/ nanos 1.0E3))))

(defn format-stats
  [stats]
  (-> stats
      (update-in [:n] format-int)
      (update-in [:name] name)
      (update-in [:sum] format-nanoseconds)
      (update-in [:min] format-nanoseconds)
      (update-in [:max] format-nanoseconds)
      (update-in [:mad] format-nanoseconds)
      (update-in [:mean] format-nanoseconds)))

(defn format-agg-stats
  [agg-stats]
  (-> agg-stats
      (update-in [:agg-sum] (fnil format-nanoseconds 0))))

(defn tableify-agg-stats
  [agg-stats]
  (for [[k v] (format-agg-stats agg-stats)]
    {:stat k :value v}))

(defn print-summary
  ([] (print-summary *current-session*))
  ([session]
     (let [{:keys [agg-stats stats]} (summary session)
           formatted-stats (map format-stats stats)
           agg-stats-table (tableify-agg-stats agg-stats)]
       (clojure.pprint/print-table
        [:name :n :sum :min :max :mad :mean]
        formatted-stats)
       (newline)
       (clojure.pprint/print-table agg-stats-table))))

(defmacro profile [& BODY]
  `(with-session
     (let [val# (do ~@BODY)]
       (print-summary)
       val#)))

(comment
  (defn my-add [a b] (+ a b))
  (defn my-mult [a b] (* a b))
  (profile-defs my-add my-mult)
  (profile
   (my-add (my-mult (rand-int 100000) (rand-int 1000000))
           (my-mult (rand-int 100000) (rand-int 1000000)))))
