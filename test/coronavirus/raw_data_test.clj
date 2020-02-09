(ns coronavirus.raw-data-test
  (:require [clojure.test :refer :all]
            [coronavirus.raw-data :refer :all]))

(def va
  (atom
   [{:day "a" :s [
                  {:n "a1" :cnt {:d 100 :c 200 :r 300}}
                  {:n "a2" :cnt {:d 101 :c 201 :r 301}}
                  ]}
    {:day "b" :s [
                  {:n "b1" :cnt {:d 400 :c 500 :r 600}}
                  {:n "b2" :cnt {:d 401 :c 501 :r 601}}
                  ]}]))

(def na {:day "a" :s [{:n "a1" :cnt {:d 21 :c 31 :r 41}}
                      {:n "a2" :cnt {:d 22 :c 32 :r 42}}
                      {:n "a3" :cnt {:d 23 :c 33 :r 43}}]})

(def nb {:day "b" :s [{:n "b1" :cnt {:d 50 :c 61 :r 71}}
                      {:n "b2" :cnt {:d 51 :c 62 :r 72}}
                      {:n "b3" :cnt {:d 53 :c 63 :r 73}}]})

(def vf
  (atom
   [{:day "Feb06" :normal "0206_0000"
     :sheets
     [{:name "Feb06_0118PM" :normal "0206_1318" :count {:c 28353 :d 565 :r 1382}}
      {:name "Feb06_0805PM" :normal "0206_2005" :count {:c 30877 :d 636 :r 1499}}]
     }]))

(def nf
  {:day "Feb06" :normal "0206_0000"
   :sheets
   [
    {:name "Feb06_0905PM" :normal "0206_2105" :count {:c 44444 :d 666 :r 2222}}
    {:name "Feb06_1005PM" :normal "0206_2205" :count {:c 55555 :d 777 :r 3333}}
    ]
   }
  )

(deftest first-test
  (is
   (= [{:day "Feb06" :normal "0206_0000"
        :sheets
        [{:name "Feb06_0118PM" :normal "0206_1318" :count {:c 28353 :d 565 :r 1382}}
         {:name "Feb06_1005PM" :normal "0206_2205" :count {:c 55555 :d 777 :r 3333}}
         {:name "Feb06_0805PM" :normal "0206_2005" :count {:c 30877 :d 636 :r 1499}}
         {:name "Feb06_0905PM" :normal "0206_2105" :count {:c 44444 :d 666 :r 2222}}
         ]
        }]
      (update-coll-of-hms--with--coll-of-new-hms @vf [nf])
      )))
