(ns corona.api.v1
  (:require [clojure.core.memoize :as memo]
            [corona.common :refer [api-server time-to-live]]
            [corona.core :as c])
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

(defn for-case [case]
  (->> (get-in (data-memo) [case :locations])
       (filter (fn [loc]
                 true
                 #_(corona.core/in? ccs (:country_code loc))))
       (map (fn [loc]
              (let [cc (:country_code loc)]
                (->> (sort-by
                      :f
                      (map (fn [[f v]] {:cc cc :rf f :f (fmt f) case v})
                           (:history loc)))
                     #_(take-last
                      #_2
                      2)))))
       (flatten)
       (group-by :f)
       (map (fn [[f hms]]
              (map (fn [[cc hms]]
                     {:cc cc :f f case (reduce + (map case hms))})
                   (group-by :cc hms))))
       (flatten)
       (sort-by :cc)))

(defn pic-data []
  (apply map
         (fn [{:keys [cc f confirmed] :as cm}
             {:keys [recovered] :as rm}
             {:keys [deaths] :as dm}]
           (let [prm {:cc cc :f f :c confirmed :r recovered :d deaths}]
             (assoc
              #_prm
              (dissoc prm :c)
              :i (c/calculate-ill prm))))
         (map for-case [:confirmed :recovered :deaths])))
