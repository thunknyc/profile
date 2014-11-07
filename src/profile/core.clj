(ns profile.core)

(defn profile-session [] (atom {}))

(def ^:dynamic *profile-data* (profile-session))

(defmacro with-session [& BODY]
  `(binding [*profile-data* (profile-session)]
     ~@BODY))

(defn clear-profile-data []
  (reset! *profile-data* {}))



(defn ^:private accrue-time
  [session name nanos]
  (swap! @session #(update-in % [name] (fnil conj []) nanos)))

(defn ^:private profile-fn
  [session f var]
  (with-meta (fn [& args]
               (let [nano-now (System/nanoTime)
                     val (apply f args)
                     elapsed (- (System/nanoTime) nano-now)]
                 (accrue-time session var elapsed)
                 val))
    {::profiled (deref var)}))

(defn profiled? [f]
  (::profiled (meta f)))

(defmacro profile-var [VAR]
  `(if-let [f# (profiled? ~VAR)]
     f#
     (alter-var-root (var ~VAR)
                     #(profile-fn (var *profile-data*) % (var ~VAR)))))

(defmacro profile-vars [& VARS]
  (let [FORMS (for [VAR VARS] `(profile-var ~VAR))]
    `(do ~@FORMS)))

(defmacro unprofile-var [VAR]
  `(when-let [f# (profiled? ~VAR)]
     (alter-var-root (var ~VAR) (fn [_#] f#))))

(defmacro unprofile-vars [& VARS]
  `(doseq [VAR# ~VARS] (profile-var VAR#)))

(defmacro toggle-profile-var [VAR]
  `(if (profiled? ~VAR)
     (unprofile-var ~VAR)
     (profile-var ~VAR)))



(defn ^:private entry-stats
  [entry]
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
      (update-in [:mean] format-nanoseconds)))

(defn ^:private format-agg-stats
  [agg-stats]
  (-> agg-stats
      (update-in [:agg-sum] (fnil format-nanoseconds 0))))

(defn ^:private tableify-agg-stats
  [agg-stats]
  (for [[k v] (format-agg-stats agg-stats)]
    {:stat k :value v}))

(defn print-summary
  ([] (print-summary *profile-data*))
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
