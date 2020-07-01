(ns corona.bot.main
  (:require
   [clj-time-ext.core :as te]
   [clj-time.core :as t]
   [corona.bot.core :as c]
   [corona.bot.telegram :as telegram]
   [corona.bot.web :as web])
  (:import
   java.time.ZoneId
   java.util.TimeZone))

(defn -main [& [port]]
  (let [msg (str "Starting " c/env-type " -main...")]
    (let [tbeg (te/tnow)
          log-fmt "[%s%s%s %s] %s\n"]
      (printf log-fmt tbeg " " "          " c/bot-ver msg)
      (do
        (if (= (str (t/default-time-zone))
               (str (ZoneId/systemDefault))
               (.getID (TimeZone/getDefault)))
          (println (str "[" (te/tnow) " " c/bot-ver "]")
                   "TimeZone:" (str (t/default-time-zone)))
          (println (str "[" (te/tnow) " " c/bot-ver "]")
                   (format (str "t/default-time-zone %s; "
                                "ZoneId/systemDefault: %s; "
                                "TimeZone/getDefault: %s\n")
                           (t/default-time-zone)
                           (ZoneId/systemDefault )
                           (.getID (TimeZone/getDefault)))))
        (pmap (fn [fn-name] (fn-name)) [telegram/-main web/-main]))
      (printf log-fmt tbeg ":" (te/tnow)    c/bot-ver (str msg " done")))))
