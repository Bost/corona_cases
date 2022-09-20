;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.keywords)

(ns corona.keywords)

;; (set! *warn-on-reflection* true)

;; k as in keyword
(def kabs "absolute" #_:α :abs)
(def ksum "sum" #_:Σ #_:σ :sum)
(def krnk "ranking = σειρά κατάταξης [seirá katátaxis]"
  #_:ξ :rnk)
(def kest "estimate/assessment = εκτίμηση [ektímisi]"
  #_:ε :est)
(def krep "reported"
  #_:ρ :rep)
(def k1e5 "per 100 0000"
  #_:κ :1e5)
(def k%%% "percent = τοις εκατό [tois ekató]"
  #_:τ :%%%)
(def kls7 "last 7"
  #_:7 :ls7)
(def kavg "average = arithmetic mean"
  #_:ø :avg)
(def kchg "change"
  #_:Δ :chg)
(def kmax "maximum = μέγιστο [mégisto]"
  #_:μ :max)

(def kcco "country code"
  :cco)
(def ktst "timestamp"
  :t)
(def kpop "population"
  :p)
(def kvac "vaccinated"
  :v)
(def kact "active"
  :a)
(def krec "recovered"
  :r)
(def kdea "deaths"
  :d)
(def knew "new confirmed"
  :n)
(def kclo "closed"
  :c)

(def ka1e5 :a1e5)
(def kr1e5 :r1e5)
(def kc1e5 :c1e5)
(def kd1e5 :d1e5)
(def kv1e5 :v1e5)

(def kcase-kw
  #_:case-kw
  :ckw)

(def klense-fun
  :lense-fun)

(defn makelense [& case-kws] (apply vector case-kws))

#_{
 [kact kabs] (makelense kact kest kabs)
 [kact k1e5] (makelense kact kest k1e5)

 [krec kabs] (makelense krec kest kabs)
 [krec k1e5] (makelense krec kest k1e5)

 [kclo kabs] (makelense kclo kest kabs)
 [kclo k1e5] (makelense kclo kest k1e5)

 [kdea kabs] (makelense kdea krep kabs)
 [kdea k1e5] (makelense kdea krep k1e5)

 [kvas kabs] (makelense kvac krep kabs)
 [kvas k1e5] (makelense kvac krep k1e5)

 [kpop kabs] (makelense kpop krep kabs)
 }

(def lense-map
  {kact  (makelense kact kest kabs)  ;; can be only estimated
   ka1e5 (makelense kact kest k1e5)
   krec  (makelense krec kest kabs)    ;; can be only estimated
   kr1e5 (makelense krec kest k1e5)
   kclo  (makelense kclo kest kabs)    ;; can be only estimated
   kc1e5 (makelense kclo kest k1e5)
   kdea  (makelense kdea krep kabs)    ;; reported
   kd1e5 (makelense kdea krep k1e5) ;; reported

   knew  (makelense knew krep kabs)

   kv1e5 (makelense kvac krep kabs) ;; reported
   kpop  (makelense kpop) ;; TODO population can be also estimated and reported i.e. absolute
   })

(def lense-map-with-strings
  (into lense-map
        {:s (makelense :es) ;; :s is a string - could be used for translations
         }))

(defn getlense-map
  [m kw]
  ((comp
    (partial apply get m)
    ;; second element is for `not-found` parameter of `get`
    (fn [kw] [kw (makelense kw krep kabs)]))
   kw))

(defn basic-lense [kw]
  (getlense-map lense-map-with-strings kw))

(defn ranking-lense
  [kw]
  (conj (getlense-map lense-map kw) krnk))

(defn identity-lense "" [kw]
  ((comp
    vector)
   kw))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.keywords)
