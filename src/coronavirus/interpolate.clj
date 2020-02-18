(ns coronavirus.interpolate
  #_(:use [incanter core stats charts io interpolation])
  (:require [incanter
             [charts :as charts]
             [core :as core]
             [stats :as stats]
             [interpolation :as interp]
             ]))
(defn plot [points fn]
  (-> (charts/function-plot fn 0 (count points))
      (charts/add-points (map first points)
                         (map second points))
      #_(core/view)))


#_(core/view (plot interp))

(defn go [points]
  (let [chart (plot points (interp/interpolate points :linear-least-squares))
        out-stream (java.io.ByteArrayOutputStream.)
        in-stream (do
                    (core/save chart out-stream :width 800 :height 600)
                    (java.io.ByteArrayInputStream.
                     (.toByteArray out-stream)))]
    in-stream))

;; (use '(incanter core stats charts))
;; (def hist (histogram (sample-normal 1000)))
;; (view hist)
;; (def plot (.getPlot hist))
;; (.setPaint (.getRenderer plot) java.awt.Color/RED)
