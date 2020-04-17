(ns corona.api.v1
  (:require [clojure.core.memoize :as memo]
            [corona.common :refer [api-server time-to-live]]
            [corona.country-codes :refer :all]
            [corona.core :as c]
            [corona.tables :as t]
            [net.cgrand.xforms :as x]
            [corona.countries :as cr])
  (:import java.text.SimpleDateFormat
           java.util.TimeZone))

;; https://coronavirus-tracker-api.herokuapp.com/v2/locations?source=jhu&timelines=true

(def url (format "http://%s/all" api-server))

(defn data [] (c/get-json url))

(def data-memo (memo/ttl data {} :ttl/threshold (* time-to-live 60 1000)))

#_(require '[ clojure.inspector :as i])
#_(i/inspect (data-memo))

(defn raw-dates-unsorted []
  #_[(keyword "2/22/20") (keyword "2/2/20")]
  ((comp keys :history last :locations :confirmed) (data-memo)))

(defn keyname [key] (str (namespace key) "/" (name key)))

;; avoid creating new class each time the `fmt` function is called
(def sdf (let [sdf (new SimpleDateFormat "MM/dd/yy")]
           (.setTimeZone sdf (TimeZone/getTimeZone
                              #_"America/New_York"
                              "UTC"))
           sdf))

(defn fmt [raw-date] (.parse sdf (keyname raw-date)))

;; (defn for-case [case]
;;   (->> (get-in (data-memo) [case :locations])
;;        (filter (fn [loc]
;;                  true
;;                  #_(corona.core/in? ccs (:country_code loc))))
;;        (map (fn [loc]
;;               (let [cc (:country_code loc)]
;;                 (->> (sort-by
;;                       :f
;;                       (map (fn [[f v]] {:cc cc :f (fmt f) case v})
;;                            (:history loc)))
;;                      #_(take-last 3)))))
;;        (flatten)
;;        (group-by :f)
;;        (map (fn [[f hms]]
;;               (map (fn [[cc hms]]
;;                      {:cc cc :f f case (reduce + (map case hms))})
;;                    (group-by :cc hms))))
;;        (flatten)
;;        (sort-by :cc)))

(def ccs
  #{
    ;; "CR" "TG" "ZA" "PE" "LC" "CH" "RU" "SI" "AU" "KR" "IT" "FI" "SC" "TT" "MY"
    ;; "SY" "MN" "AM" "DZ" "UY" "TD" "DJ" "BI" "MK" "MU" "LI" "GR" "GY" "CG" "ML"
    ;; "GM" "SA" "BH" "NE" "BN" "XK" "CD" "DK" "BJ" "ME" "BO" "JO" "CV" "VE" "CI"
    ;; "UZ" "TN" "IS" "GA" "TZ" "AT" "LT" "NP" "BG" "IL" "PK" "PT" "HR" "MR" "GE"
    ;; "HU" "TW" "MM" "SR" "VA" "KW" "SE" "GB" "QQ" "VN" "CF" "PA" "VC" "JP" "IR"
    ;; "AF" "LY" "MZ" "RO" "QA" "CM" "BY" "SD" "AR" "BR" "ZW" "NZ" "FJ" "ID" "SV"
    ;; "CN" "HT" "RW" "BA" "TL" "JM" "KE" "PY" "CY" "GH" "MA" "SG" "LK" "PH" "SM"
    ;; "TR" "PS" "BZ" "CU" "AD" "DM" "LR" "OM" "SO" "DO" "AL" "FR" "GW" "BB" "CA"
    ;; "MG" "KH" "LA" "HN" "TH" "DE" "LB" "KZ" "EC" "NO" "AO" "ET" "MD" "AG" "BE"
    ;; "MV" "SZ" "CZ" "CL" "BT" "NL" "EG" "SN" "EE" "KN" "BW" "NI" "PG" "IQ" "KG"
    ;; "US" "ZM" "MC" "GT" "BF" "LU" "UA" "IE" "LV" "GD" "MW" "BS" "AZ" "SK" "GQ"
    ;; "IN" "ES" "CO" "RS" "NG" "UG" "SL" "ER" "AE" "BD" "MT" "GN" "NA" "MX" "PL"
    })

(defn xf-for-case
  "({:cc \"DE\" :f #inst \"2020-04-14T00:00:00.000-00:00\" :c 131359 :r 68200 :d 3294 :p 83783942 :i 59865}
    {:cc \"DE\" :f #inst \"2020-04-15T00:00:00.000-00:00\" :c 134753 :r 72600 :d 3804 :p 83783942 :i 58349}
    {:cc \"DE\" :f #inst \"2020-04-16T00:00:00.000-00:00\" :c 137698 :r 77000 :d 4052 :p 83783942 :i 56646}
    {:cc \"SK\" :f #inst \"2020-04-14T00:00:00.000-00:00\" :c 835    :r 113   :d 2    :p 5459642  :i 720}
    {:cc \"SK\" :f #inst \"2020-04-15T00:00:00.000-00:00\" :c 863    :r 151   :d 6    :p 5459642  :i 706}
    {:cc \"SK\" :f #inst \"2020-04-16T00:00:00.000-00:00\" :c 977    :r 167   :d 8    :p 5459642  :i 802})

  TODO
  'transducer' for flatten - see https://clojuredocs.org/clojure.core/mapcat"
  [case]

  (defn process-location [{:keys [country_code history]}]
    (transduce (comp (x/sort-by :f)
                     (map (fn [[f v]] {:cc country_code :f (fmt f) case v}))
                     (x/take-last 3))
               conj [] history))

  (defn process-date [[f hms]]
    (map (fn [[cc hms]]
           {:cc cc :f f case (reduce + (map case hms))})
         (group-by :cc hms)))

  (let [xform (comp (filter (fn [loc]
                              true
                              #_(corona.core/in? ccs (:country_code loc))))
                    (map process-location))
        coll (transduce xform into []
                        (get-in (data-memo) [case :locations]))]
    (let [xform (comp
                 (x/by-key :f (x/into []))
                 (map process-date))]
      (sort-by :cc
               (transduce xform into [] coll)))))

(defn pic-data []
  (let [population (t/population)]
    (apply map
           (fn [{:keys [cc f confirmed] :as cm}
               {:keys [recovered] :as rm}
               {:keys [deaths] :as dm}]
             (let [prm {:cc cc :f f :c confirmed :r recovered :d deaths
                        :p (if-let [p (get population cc)]
                             p
                             (do
                               (printf "No population found for %s %s; using 0\n"
                                       (cr/country-name cc) cc)
                               0))}]
               (assoc
                prm
                #_(dissoc prm :c)
                :i (c/calculate-ill prm))))
           (map xf-for-case [:confirmed :recovered :deaths]))))
