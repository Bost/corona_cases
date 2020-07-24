(ns corona.time)

;; TODO move it this to clj-time-ext
(defn tstp
  "Return a string containing current timestamp, formatted using `pattern` and
  shortened to the length of the `pattern`. E.g.:
  (tstp \"HHmmss.nnn\")
  ;; => \"161644.809\"
  (tstp \"HHmmss.nnn\" \"Europe/London\")
  ;; => \"151644.809\"
  (tstp \"HHmmss.nnn\" \"Europe/Berlin\")
  ;; => \"161644.809\"
  "
  ([] (tstp "HHmmss.nnn" (str (java.time.ZoneId/systemDefault))))
  ([pattern] (tstp pattern (str (java.time.ZoneId/systemDefault))))
  ([pattern zone-id]
   (let [
         ;; pattern "HHmmss.nnnn"
         fmt (java.time.format.DateTimeFormatter/ofPattern pattern)
         date (java.time.LocalDateTime/now (java.time.ZoneId/of zone-id))]
     (subs (.format date fmt) 0 (count pattern)))))

(defn tstp3 "Return timestamp. E.g. 112147.123"       [] (tstp))

(defn tnow
  "Return a string containing current timestamp. E.g.
  (tnow)
  ;; => \"161644.809\"
  (tnow \"Europe/London)
  ;; => \"151644.809\"
  "
  ([] (tstp3))
  ([zone-id] (tstp "HHmmss.nnn" zone-id)))
