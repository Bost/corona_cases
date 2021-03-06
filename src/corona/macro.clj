(ns corona.macro)

(defmacro defn-fun-id
  "Docstring is mandatory!"
  [name docstr args & exprs]
  `(def ~name
     ~docstr
     (fn ~args (let [~'fun-id (str (quote ~name))]
                 ~@exprs))))

#_(defn-fun-id f "f-docs" [x]
  {:pre [(number? x)]}
  (printf "[%s] hi\n" fun-id)
  (inc x))

#_(defn-fun-id g "f-docs" [x]
  (printf "[%s] hi\n" fun-id)
  (inc (f x)))

(defmacro debugf [s & exprs] `(taoensso.timbre/debugf (str "[%s] " ~s) ~'fun-id ~@exprs))
(defmacro infof  [s & exprs] `(taoensso.timbre/infof  (str "[%s] " ~s) ~'fun-id ~@exprs))
(defmacro warnf  [s & exprs] `(taoensso.timbre/warnf  (str "[%s] " ~s) ~'fun-id ~@exprs))
(defmacro errorf [s & exprs] `(taoensso.timbre/errorf (str "[%s] " ~s) ~'fun-id ~@exprs))
(defmacro fatalf [s & exprs] `(taoensso.timbre/fatalf (str "[%s] " ~s) ~'fun-id ~@exprs))

(defmacro system-ok? []
  `((comp
     (fn [~'v] (taoensso.timbre/infof "[%s] system-ok? %s" ~'fun-id ~'v)
       ~'v))
    (if (or (= ~'fun-id "-main")
            (= corona.common/env-type :devel))
      (do
        #_(taoensso.timbre/infof
         (str "[%s] do-check"
              " (= ~'fun-id \"-main\"): %s;"
              " (= corona.common/env-type :devel): %s")
         ~'fun-id
         (= ~'fun-id "-main")
         (= corona.common/env-type :devel))
        (let [~'dbase-ok? (corona.models.migration/migrated?)]
          (taoensso.timbre/infof "[%s]\n  %s"
                                 ~'fun-id
                                 (clojure.string/join
                                  "\n  "
                                  (corona.common/show-env)))
          (taoensso.timbre/infof "[%s] dbase-ok? %s" ~'fun-id ~'dbase-ok?)
          ~'dbase-ok?))
      (do
        #_(taoensso.timbre/infof
         (str "[%s] no-check"
              " (= ~'fun-id \"-main\"): %s;"
              " (= corona.common/env-type :devel): %s")
         ~'fun-id
         (= ~'fun-id "-main")
         (= corona.common/env-type :devel))
        true))))

;; (defn-fun-id foo "docstr" []
;;   (debugf "some %s text %s" 'a 1)
;;   (debugf "%s" {:a 1 :b 2})
;;   1)

#_(defmacro debugf [s & exprs] `((quote ~'debugf) (str "[%s] " ~s) ~'fun-id ~@exprs))

#_(defmacro defn-fun-id2
  "Docstring is mandatory!"
  [name docstr args & exprs]
  `(def ~name
     ~docstr
     (fn ~args
       (let [fun-id# (str (quote ~name))]
         (fn debugf [s & exprs] `(taoensso.timbre/debugf (str "[%s] " ~s) fun-id# ~@exprs))
         (fn infof  [s & exprs] `(taoensso.timbre/infof  (str "[%s] " ~s) fun-id# ~@exprs))
         (fn warnf  [s & exprs] `(taoensso.timbre/warnf  (str "[%s] " ~s) fun-id# ~@exprs))
         (fn errorf [s & exprs] `(taoensso.timbre/errorf (str "[%s] " ~s) fun-id# ~@exprs))
         (fn fatalf [s & exprs] `(taoensso.timbre/fatalf (str "[%s] " ~s) fun-id# ~@exprs))
         ~@exprs))))

;; https://clojurians.zulipchat.com/#narrow/stream/151168-clojure/topic/macro.20for.20transforming.20let.20into.20def/near/215975427
;; https://clojurians.zulipchat.com/#narrow/stream/151168-clojure/topic/core.20nuggets/near/218333667
