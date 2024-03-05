(ns corona.utils.core
  #_
  (:require
   [utils.debug :as utd :refer [dbg]]))

(defn all-pairwise-equal?
  "true if the elements of every collection from `colls` are equal to each
  other, only within a particular collection.

  Accepts pairs:
  (all-pairwise-equal? [1 1] [2 2])         ;; => true
  (all-pairwise-equal? [1 1] [2 3])         ;; => false
  (all-pairwise-equal? [1 1] [3 3])         ;; => true
  (all-pairwise-equal? [1 1] [3 3] [4])     ;; => true
  (all-pairwise-equal? [1 1] [3 3] [4 nil]) ;; => false
  "
  [& colls]
  ((comp
    (partial every? true?) ;; i.e. (reduce and [...])
    (partial map (partial apply =)))
   colls))

(def _m :m)
(def _f :f)
(def _e :e)
(def _ks :ks)
(def _km :km)
(def _not-found :not-found)
(def _args :args)
(def _coll :coll)

(defn rename-keys
  ""
  ([m km]
   (rename-keys _m m _km km))
  ([k1 v1 v2]
   ;; let's be smarter - assuming last parameter is the map
   (rename-keys k1 v1 _m v2))
  ([{:keys [m km]}]
   (rename-keys _m m _km km))
  ([k1 v1 k2 v2]
   (cond
     (all-pairwise-equal? [k1 _m] [k2 _km])
     (clojure.set/rename-keys v1 v2)

     (all-pairwise-equal? [k1 _km] [k2 _m])
     (clojure.set/rename-keys v2 v1)

     :else
     (throw (IllegalArgumentException.
             (print-str "Some keywords are miss-spelled:" k1 k2))))))

(defn in?
  "
  ;; Handy when `partial` is applied:
  (map (partial in? :e 1 :coll) [[ 0  1  2  3  4]
                                 [10 11 12 13 14]])
  ;; => (true false)

  ;; Parameters in the hash-map
  (in? {:e 1 :coll [0 1 2 3 4]}) => true

  ;; Classic call:
  (in? [0 1 2 3 4] 1)            => true
  (in? 1 [0 1 2 3 4])            => IllegalArgumentException

  ;; Named parameters with variable order:
  (in? :coll [0 1 2 3 4] :e 1)   => true
  (in? :e 1 :coll [0 1 2 3 4])   => true
  ;; Named parameters with variable order even in a hash-map:
  (in? {:coll [0 1 2 3 4] :e 1}) => true

  ;; If the last parameter is the map then the ':coll' keyword can be
  ;; omitted...
  (in? :e 1 [0 1 2 3 4])         => true
  ;; ... and it works even when `partial` is applied:
  (map (partial in? :e 1) [[ 0  1  2  3  4]
                           [10 11 12 13 14]])
  ;; => (true false)

  ;; Mixed calls don't work (TODO: but they could work!):
  (in? [0 1 2 3 4] :e 1)
  ;; => Execution error (IllegalArgumentException) at ...
  ;;    Some keywords are miss-spelled: [0 1 2 3 4] :coll
  (in? :coll [0 1 2 3 4] 1)  => ArityException
  ;; => Execution error (IllegalArgumentException) at ...
  ;;    Some keywords are miss-spelled: :coll :coll"
  ([v1 v2]
   (in? _coll v1 _e v2))
  ([k1 v1 v2]
   ;; let's be smarter - assuming last parameter is the collection
   (in? k1 v1 _coll v2))
  ([{:keys [coll e]}]
   (in? _coll coll _e e))
  ([k1 v1 k2 v2]
   (cond
     (all-pairwise-equal? [k1 _coll] [k2 _e])
     (boolean (some (partial = v2) v1))

     (all-pairwise-equal? [k1 _e] [k2 _coll])
     (boolean (some (partial = v1) v2))

     :else
     (throw (IllegalArgumentException.
             (print-str "Some keywords are miss-spelled:" k1 k2))))))

(defn select-keys
  "Syntax sugar on top of clojure.core/select-keys

  ;; Handy when `partial` is applied:
  (map (partial select-keys :ks [:a :b] :m)
     [{:a 11 :b 12 :c nil}
      {:a 21 :b 22 :c nil}])
  ;; => ({:a 11, :b 12} {:a 21, :b 22})

  ;; Classic call:
  (select-keys {:a 11 :b 12 :c nil} [:a :b])                => {:a 11, :b 12}

  ;; Named parameters with variable order:
  (select-keys :m {:a 11 :b 12 :c nil} :ks [:a :b])   => {:a 11, :b 12}
  (select-keys :ks [:a :b] :m {:a 11 :b 12 :c nil})   => {:a 11, :b 12}
  ;; Named parameters with variable order even in a hash-map:
  (select-keys {:m {:a 11 :b 12 :c nil} :ks [:a :b]}) => {:a 11, :b 12}

  ;; If the last parameter is the map then the ':m' keyword can be
  ;; omitted...
  (select-keys :ks [:a :b] {:a 11 :b 12 :c nil})
  ;; ... and it works even when `partial` is applied:
  (map (partial select-keys :ks [:a :b] :m)
     [{:a 11 :b 12 :c nil}
      {:a 21 :b 22 :c nil}])
  ;; => ({:a 11, :b 12} {:a 21, :b 22})

  ;; Mixed calls don't work:
  (select-keys {:a 11 :b 12 :c nil} :ks [:a :b])
  ;; => Execution error (ArityException) at ...
  ;;    Wrong number of args (3) passed to: clojure.core/select-keys
  (select-keys :m {:a 11 :b 12 :c nil} [:a :b])
  ;; => Execution error (ArityException) at ...
  ;;    Wrong number of args (3) passed to: clojure.core/select-keys"
  ([v1 v2]
   (select-keys _m v1 _ks v2))
  ([k1 v1 v2]
   ;; let's be smarter - assuming last parameter is the map
   (select-keys k1 v1 _m v2))
  ([{:keys [m ks]}]
   (select-keys _m m _ks ks))
  ([k1 v1 k2 v2]
   (cond
     (all-pairwise-equal? [k1 _m] [k2 _ks])
     (clojure.core/select-keys v1 v2)

     (all-pairwise-equal? [k1 _ks] [k2 _m])
     (clojure.core/select-keys v2 v1)

     :else
     (throw (IllegalArgumentException.
             (print-str "Some keywords are miss-spelled:" k1 k2))))))

(defn update-in
  "Syntax sugar on top of clojure.core/update-in
  "
  ([{:keys [m ks f args] :or {args nil}}]
   (update-in _m m _ks ks _f f _args args))
  ([m ks f]
   (update-in _m m _ks ks _f f))
  ([m ks f args] ;; the 4th parameter `args` is optional!
   (update-in _m m _ks ks _f f _args args))
  ([k1 v1 k2 v2 v]
   (update-in k1 v1 k2 v2 _m v))
  ([k1 v1 k2 v2 k3 v3]  ;; the 4th parameter `args` is optional!
   (cond
     (all-pairwise-equal? [k1 _m] [k2 _ks] [k3 _f])
     (clojure.core/update-in v1 v2 v3)

     (all-pairwise-equal? [k1 _ks] [k2 _f] [k3 _m])
     (clojure.core/update-in v3 v1 v2)

     :else
     (throw (IllegalArgumentException.
             (print-str "Some keywords are miss-spelled:" k1 k2 k3)))))
  ([k1 v1 k2 v2 k3 v3 k4 v4]
   (cond
     (all-pairwise-equal? [k1 _m] [k2 _ks] [k3 _f] [k4 _args])
     (clojure.core/update-in v1 v2 v3 v4)

     (all-pairwise-equal? [k1 _ks] [k2 _f] [k3 _m] [k4 _args])
     (clojure.core/update-in v3 v1 v2 v4)

     :else
     (throw (IllegalArgumentException.
             (print-str "Some keywords are miss-spelled:" k1 k2 k3 k4))))))
(defn get-in
  "Syntax sugar on top of clojure.core/get-in

  (map (partial get-in :ks [:b] :m) [{:a 0 :b 2} {:a 1 :b 22}]) => (2 22)
  (get-in {:a 0 :b 2} [:b] :not-found)                          => 2
  (get-in {:a 0 :b 2} [:x] :not-found)                          => :not-found
  "
  ([{:keys [m ks not-found] :or {not-found nil}}]
   (get-in _m m _ks ks _not-found not-found))
  ([m ks]
   (get-in _m m _ks ks))
  ([k1 v1 v]
   (cond
     (all-pairwise-equal? [k1 _ks])
     (get-in k1 v1 _m v)

     :else
     (get-in _m k1 _ks v1 _not-found v)))
  ([k1 v1 k2 v2]
   (get-in k1 v1 k2 v2 _not-found nil))
  ([k1 v1 k2 v2 k3 v3]
   (cond
     (all-pairwise-equal? [k1 _ks] [k2 _m] [k3 _not-found])
     (clojure.core/get-in v2 v1 v3)

     (all-pairwise-equal? [k1 _m] [k2 _ks] [k3 _not-found])
     (clojure.core/get-in v1 v2 v3)

     :else
     (throw (IllegalArgumentException.
             (print-str "Some keywords are miss-spelled:" k1 k2 k3))))))

(defn assoc-in
  "Syntax sugar on top of clojure.core/assoc-in

  TODO"
  [m [k & ks] v]
  clojure.core/assoc-in m k ks v)

(defn wrap-in-hooks
  "See also https://github.com/MichaelDrogalis/dire

  Add pre- and post-hooks / advices around `main-fun`.

  The first argument of the post-hook is the return-value from the `main-fun`,
  i.e. it is an one extra argument! E.g.:

  (defn main-fun-1-arg [x] (inc x))
  (def wrapped-main-fun-1-arg
  (wrap-in-hooks
    {:pre  (fn [x] (printf \"      pre-hook x: %s\n\" x))
     :post (fn [retval x] (printf \" main-fun retval: %s\n     post-hook x: %s\n\" retval x))}
    main-fun-1-arg))
  (wrapped-main-fun-1-arg 1) ; =>
  ;;      pre-hook x: 1
  ;; main-fun retval: 2
  ;;     post-hook x: 1


  (defn main-fun-2-args [x y] (+ x y))
  (def wrapped-main-fun-2-args
  (wrap-in-hooks
    {:pre  (fn [x y] (printf \"      pre-hook x: %s, y: %s\n\" x y))
     :post (fn [retval x y] (printf \" main-fun retval: %s\n     post-hook x: %s, y: %s\n\" retval x y))}
    main-fun-2-args))
  (wrapped-main-fun-2-args 3 4) ; =>
  ;;      pre-hook x: 3, y: 4
  ;; main-fun retval: 7
  ;;     post-hook x: 3, y: 4


  The hooks /advices for a multi-arity `main-fun` must have corresponding
  signatures as the `main-fun`. E.g.:

  (defn multi-arity-main-fun
    ([] (multi-arity-main-fun 42))
    ([x] x))

  (def wrapped-multi-arity-main-fun
    (wrap-in-hooks
       {:pre
        (fn [& args]
          (condp = (count args)
            0     (printf \"   pre-hook args: %s\n\" 'none)
            1     (printf \"   pre-hook args: %s\n\" (nth args 0))
            :else (throw (Exception.
                          (format
                           \"%s %s\n\"
                           \"Wrong argument specification.\"
                           (format \"The pre-hook has %s argument(s).\"
                                   (count args)))))))
        :post
        (fn [& args]
          (condp = (count args)
            1     (printf \" main-fun retval: %s\n  post-hook args: %s\n\" (nth args 0) 'none)
            2     (printf \" main-fun retval: %s\n  post-hook args: %s\n\" (nth args 0) (nth args 1))
            :else (throw (Exception.
                          (format
                           \"%s %s\n\"
                           \"Wrong argument specification.\"
                           (format \"The post-hook has %s argument(s).\"
                                   (count args)))))))}
       multi-arity-main-fun))

  (wrapped-multi-arity-main-fun) ; =>
  ;;   pre-hook args: none
  ;; main-fun retval: 42
  ;;  post-hook args: none

  (wrapped-multi-arity-main-fun 1) ; =>
  ;;   pre-hook args: 1
  ;; main-fun retval: 1
  ;;  post-hook args: 1"
  [{:keys [pre post]} main-fun]
  (fn [& args]
    (apply pre args)
    (let [result (apply main-fun args)]
      (apply post (cons result args)))))
