(ns coronavirus.raw-data)

(comment
  (def va
    (atom
     [{:day "o" :s [{:n "o1" :cnt {:d 0 :c -1 :r -2}}]}
      {:day "a" :s [{:n "a1" :cnt {:d 0 :c 1 :r 2}}]}]))

  (def n1 {:day "a" :s [{:n "a1" :cnt {:d 10 :c 11 :r 12}}]})
  (def nb {:day "b" :s [{:n "b1" :cnt {:d 0 :c 1 :r 2}}]})

  (swap! va (fn [_] (update-va [n1 nb]))))

(defn in?
  "true if seq contains elm"
  [seq elm]
  (boolean (some (fn [e] (= elm e)) seq)))

(defn update-coll-of-hms
  [coll-of-hms k new-hm]
  (if (in? (keys new-hm) k)
    (let [new-coll-of-hms
          (let [v (k new-hm)
                idx (->> coll-of-hms
                         (keep-indexed (fn [idx hm]
                                         #_(println "k" k "v" v)
                                         #_(println "hm" hm)
                                         (if (= (k hm) v) idx)))
                         ;; TODO a coll of one or none elems can be returned
                         (first))]
            #_(println "idx" idx "(boolean idx)" (boolean idx))
            (if idx
              (do
                #_(println (format "(assoc %s (merge ...))" idx))
                (assoc coll-of-hms idx
                       (merge (nth coll-of-hms idx) new-hm)))
              (do
                #_(println (format "(conj ... %s)" new-hm))
                (conj coll-of-hms new-hm))))]
      new-coll-of-hms)
    (println (format "Key %s not in the new-hm %s" k new-hm))))

(def update-coll-of-hms--with--coll-of-new-hms
  (fn [vec-of-hms init-new-hms]
    (loop [new-hms init-new-hms
           acc vec-of-hms]
      #_(println "acc" acc)
      (if (empty? new-hms)
        acc
        (recur (rest new-hms)
               (update-coll-of-hms acc :day (first new-hms)))))))

(def hms-day-sheets
  (atom
   [{:day "Jan22" :normal "0122_0000"
     :sheets
     [{:name "Jan22_12pm" :normal "0122_1200" :count {:c 555 :s 137}}
      {:name "Jan22_12am" :normal "0122_0000" :count {:c 332 :s 169}}]
     :count {:c 555 :s 137} :date #inst "2020-01-22T00:00:00.000-00:00"}
    {:day "Jan23" :normal "0123_0000"
     :sheets
     [{:name "Jan23_12pm" :normal "0123_1200" :count {:c 653 :s 144 :r 30 :d 18}}]
     :count {:c 653 :s 144 :r 30 :d 18} :date #inst "2020-01-23T00:00:00.000-00:00"}
    {:day "Jan24" :normal "0124_0000"
     :sheets
     [{:name "Jan24_12pm" :normal "0124_1200" :count {:c 941 :s 159 :r 36 :d 26}}
      {:name "Jan24_12am" :normal "0124_0000" :count {:c 881 :s 115 :r 34 :d 26}}]
     :count {:c 941 :s 159 :r 36 :d 26} :date #inst "2020-01-24T00:00:00.000-00:00"}
    {:day "Jan25" :normal "0125_0000"
     :sheets
     [{:name "Jan25_10pm" :normal "0125_2200" :count {:c 2019 :s 406 :r 49 :d 56}}
      {:name "Jan25_12am" :normal "0125_0000" :count {:c 1354 :s 73 :r 38 :d 41}}
      {:name "Jan25_12pm" :normal "0125_1200" :count {:c 1438 :s 404 :r 39 :d 42}}]
     :count {:c 2019 :s 406 :r 49 :d 56} :date #inst "2020-01-25T00:00:00.000-00:00"}
    {:day "Jan26" :normal "0126_0000"
     :sheets
     [{:name "Jan26_11pm" :normal "0126_2300" :count {:c 2794 :d 80 :r 54}}
      {:name "Jan26_11am" :normal "0126_1100" :count {:c 2116 :d 56 :r 52 :s 383}}]
     :count {:c 2794 :d 80 :r 54} :date #inst "2020-01-26T00:00:00.000-00:00"}
    {:day "Jan27" :normal "0127_0000"
     :sheets
     [{:name "Jan27_830pm" :normal "0127_2030" :count {:c 4473 :d 107 :r 63}}
      {:name "Jan27_7pm" :normal "0127_1900" :count {:c 2927 :d 82 :r 61}}
      {:name "Jan27_9am" :normal "0127_0900" :count {:c 2886 :d 81 :r 59}}]
     :count {:c 4473 :d 107 :r 63} :date #inst "2020-01-27T00:00:00.000-00:00"}
    {:day "Jan28" :normal "0128_0000"
     :sheets
     [{:name "Jan28_6pm" :normal "0128_1800" :count {:c 5578 :d 131 :r 107}}
      {:name "Jan28_11pm" :normal "0128_2300" :count {:c 6057 :d 132 :r 110}}
      {:name "Jan28_1pm" :normal "0128_1300" :count {:c 4690 :d 106 :r 79}}]
     :count {:c 6057 :d 132 :r 110} :date #inst "2020-01-28T00:00:00.000-00:00"}
    {:day "Jan29" :normal "0129_0000"
     :sheets
     [{:name "Jan29_130pm" :normal "0129_1330" :count {:c 6164 :d 132 :r 112}}
      {:name "Jan29_230pm" :normal "0129_1430" :count {:c 6165 :d 133 :r 126}}
      {:name "Jan29_9pm" :normal "0129_2100" :count {:c 7783 :d 170 :r 133}}]
     :count {:c 7783 :d 170 :r 133} :date #inst "2020-01-29T00:00:00.000-00:00"}
    {:day "Jan30" :normal "0130_0000"
     :sheets
     [{:name "Jan30_930pm" :normal "0130_2130" :count {:c 9776 :d 213 :r 187}}
      {:name "Jan30_11am" :normal "0130_1100" :count {:c 8235 :d 171 :r 143}}]
     :count {:c 9776 :d 213 :r 187} :date #inst "2020-01-30T00:00:00.000-00:00"}
    {:day "Jan31" :normal "0131_0000"
     :sheets
     [{:name "Jan31_7pm" :normal "0131_1900" :count {:c 11374 :d 259 :r 252}}
      {:name "Jan31_2pm" :normal "0131_1400" :count {:c 9926 :d 213 :r 222}}]
     :count {:c 11374 :d 259 :r 252} :date #inst "2020-01-31T00:00:00.000-00:00"}
    {:day "Feb01" :normal "0201_0000"
     :sheets
     [{:name "Feb01_11pm" :normal "0201_2300" :count {:c 14549 :d 305 :r 340}}
      {:name "Feb01_10am" :normal "0201_1000" :count {:c 12024 :d 259 :r 287}}
      {:name "Feb01_6pm" :normal "0201_1800" :count {:c 12038 :d 259 :r 284}}]
     :count {:c 14549 :d 305 :r 340} :date #inst "2020-02-01T00:00:00.000-00:00"}
    {:day "Feb02" :normal "0202_0000"
     :sheets
     [{:name "Feb02_5am" :normal "0202_0500" :count {:c 16823 :d 362 :r 472}}
      {:name "Feb02_745pm" :normal "0202_1945" :count {:c 16823 :d 362 :r 472}}
      {:name "Feb02_9PM" :normal "0202_2100" :count {:c 17295 :d 362 :r 487}}]
     :count {:c 17295 :d 362 :r 487} :date #inst "2020-02-02T00:00:00.000-00:00"}
    {:day "Feb03" :normal "0203_0000"
     :sheets
     [{:name "Feb03_940pm" :normal "0203_2140" :count {:c 20588 :d 426 :r 644}}
      {:name "Feb03_1230pm" :normal "0203_1230" :count {:c 17491 :d 362 :r 536}}]
     :count {:c 20588 :d 426 :r 644} :date #inst "2020-02-03T00:00:00.000-00:00"}
    {:day "Feb04" :normal "0204_0000"
     :sheets
     [{:name "Feb04_8AM" :normal "0204_0800" :count {:c 20680 :d 427 :r 723}}
      {:name "Feb04_1150AM" :normal "0204_1150" :count {:c 20704 :d 427 :r 727}}
      {:name "Feb04_10PM" :normal "0204_2200" :count {:c 24503 :d 492 :r 899}}]
     :count {:c 24503 :d 492 :r 899} :date #inst "2020-02-04T00:00:00.000-00:00"}
    {:day "Feb05" :normal "0205_0000"
     :sheets
     [{:name "Feb05_1220PM" :normal "0205_1220" :count {:c 24630 :d 494 :r 1029}}]
     :count {:c 24630 :d 494 :r 1029} :date #inst "2020-02-05T00:00:00.000-00:00"}
    {:day "Feb06" :normal "0206_0000"
     :sheets
     [{:name "Feb06_0118PM" :normal "0206_1318" :count {:c 28353 :d 565 :r 1382}}
      {:name "Feb06_0805PM" :normal "0206_2005" :count {:c 30877 :d 636 :r 1499}}]
     :count {:c 30877 :d 636 :r 1499} :date #inst "2020-02-06T00:00:00.000-00:00"}]))
