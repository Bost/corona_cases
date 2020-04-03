(ns corona.defs)

(def worldwide-country-codes {"ZZ" "ZZZ"})
(def worldwide-2-country-code (-> worldwide-country-codes keys first))
(def worldwide              "Worldwide")
(def country-code-worldwide {worldwide-2-country-code worldwide})

(def default-country-codes   {"QQ" "QQQ"}) ;: FIXME
(def default-2-country-code (-> default-country-codes keys first))
(def others                 "Others")
(def country-code-others    {default-2-country-code others})

(def country-code-cruise-ship   {default-2-country-code "Cruise Ship"})
