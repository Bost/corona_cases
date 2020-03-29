(ns corona.defs)

(def worldwide-country-codes {"ZZ" "ZZZ"})
(def worldwide-2-country-code (-> worldwide-country-codes keys first))
(def worldwide-3-country-code (-> worldwide-country-codes vals first))
(def worldwide              "Worldwide")
(def country-code-worldwide {worldwide-2-country-code worldwide})

(def default-country-codes   {"QQ" "QQQ"})
(def default-2-country-code (-> default-country-codes keys first))
(def default-3-country-code (-> default-country-codes vals first))
(def others                 "Others")
(def country-code-others    {default-2-country-code others})

(def cruise-ship-2-country-code default-2-country-code)
(def cruise-ship-3-country-code default-3-country-code)
(def cruise-ship                "Cruise Ship")
(def country-code-cruise-ship   {cruise-ship-2-country-code cruise-ship})

(def default-continent-code "CCC")

