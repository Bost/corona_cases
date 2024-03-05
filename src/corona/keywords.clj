;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.keywords)

(ns corona.keywords
  "Various keywords used not only in the data map.
  'k...' as in 'keyword'")

;; (set! *warn-on-reflection* true)

(def klist "For storing listings in the cache"
  :list)
;; (map corona.telemetry/measure [:a :Δ :x̅]) => ("104 B" "104 B" "104 B")
(def kabs "absolute"
  #_:α :abs)
(def ksum "sum"
  #_:Σ :sum) ;; σ/ς - sigma: ς is for word-final position, σ elsewhere
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
  #_:x̅ :avg) ;; x-bar
(def kchg "change"
  #_:Δ :chg)
(def kmax "maximum = μέγιστο [mégisto]"
  #_:μ :max)
(def kcco "country code = κωδικός χώρας [kodikós chóras]"
  #_:κ :cco)
(def ktst "timestamp"
  #_:t :tst)
(def kpop "population"
  #_:p :pop)
(def kvac "vaccinated"
  #_:v :vac)
(def kact "active"
  #_:a :act)
(def krec "recovered"
  #_:r :rec)
(def kdea "deaths"
  #_:d :dea)
(def knew "new confirmed"
  #_:n :new)
(def kclo "closed"
  #_:c :clo)
(def ka1e5 "active per 100 0000"
  #_:aκ :a1e5)
(def kr1e5 "recovered per 100 0000"
  #_:rκ :r1e5)
(def kc1e5 "closed per 100 0000"
  #_:cκ :c1e5)
(def kd1e5 "deaths per 100 0000"
  #_:dκ :d1e5)
(def kv1e5 "vaccinated per 100 0000"
  #_:vκ :v1e5)
(def kcase-kw "case = υπόθεση [ypóthesi]"
  #_:υ #_:case-kw)
(def klense-fun "lense = φακός [fakós]"
  #_:φ :lense-fun)

(defn makelense
  "E.g.:
  (makelense kact kest kabs) => [:act :est :abs]
  (makelense kvac krep k1e5) => [:vac :rep :1e5]"
  [& case-kws]
  (apply vector case-kws))

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
  {kact  (makelense kact kest kabs) ;; can be only estimated
   ka1e5 (makelense kact kest k1e5)
   krec  (makelense krec kest kabs) ;; can be only estimated
   kr1e5 (makelense krec kest k1e5)
   kclo  (makelense kclo kest kabs) ;; can be only estimated
   kc1e5 (makelense kclo kest k1e5)
   kdea  (makelense kdea krep kabs) ;; reported
   kd1e5 (makelense kdea krep k1e5) ;; reported

   knew  (makelense knew krep kabs)

   kv1e5 (makelense kvac krep kabs) ;; reported
   ;; TODO: Population size can be estimated and reported, i.e. absolute
   kpop  (makelense kpop)
   })

(def lense-map-with-strings
  ;; :s is a string - could be used for translations
  (into lense-map {:s (makelense :es)}))

(defn getlense-map
  "E.g.:
  (getlense-map lense-map-with-strings kpop) => [:pop]
  (getlense-map lense-map-with-strings kact) => [:act :est :abs]"
  [m kw]
  ((comp
    (partial apply get m)
    ;; second element is for `not-found` parameter of `get`
    (fn [kw] [kw (makelense kw krep kabs)]))
   kw))

(defn basic-lense
  "E.g.:
  (basic-lense kpop)  => [:pop]
  (basic-lense kact)  => [:act :est :abs]
  (basic-lense kc1e5) => [:clo :est :1e5]"
  [kw]
  (getlense-map lense-map-with-strings kw))

(defn ranking-lense
  "E.g.:
  (ranking-lense kpop) => [:pop :rnk]
  (ranking-lense kact) => [:act :est :abs :rnk]"
  [kw]
  (conj (getlense-map lense-map kw) krnk))

(defn identity-lense
  "E.g.:
  (identity-lense kpop) => [:pop]
  (identity-lense kact) => [:act]"
  [kw]
  ((comp
    vector)
   kw))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.keywords)
