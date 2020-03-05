(ns corona.interpolate
  (:require
   [incanter.charts :as charts]
   [incanter.core :as core]
   [incanter.interpolation :as interp]))

(defn degree [points]
  #_21
  (Math/round (* 0.55 (count points)))
  #_0
  #_(quot (count points) 5)       ;; 1/5
  #_(quot (count points) 4)       ;; 1/4
  #_(quot (count points) 3)       ;; 1/3
  #_(quot (count points) 2)       ;; 1/2
  #_(* 2 (quot (count points) 3)) ;; 2/3
  #_(* 3 (quot (count points) 4)) ;; 3/4
  #_(* 3 (quot (count points) 5)) ;; 3/5
  #_(count points))

(defn plot [title points fun]
  (-> (charts/parametric-plot fun 0 1
       :title title #_(str "degree" degree)
       :x-label "Jan12 + <day-number>" :y-label "Cases")
      (charts/add-points (map first points)
                         (map second points))
      #_(core/view)))

(defn interpolate-points [points]
  (interp/interpolate-parametric points :b-spline :degree (degree points)))

(defn create-pic [title vals]
  (let [points (mapv (fn [x y] [x y]) (range) vals)
               #_[[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]]
        chart (plot title points (interpolate-points points))
        out-stream (java.io.ByteArrayOutputStream.)
        in-stream (do
                    (core/save chart out-stream :width 800 :height 600)
                    (java.io.ByteArrayInputStream.
                     (.toByteArray out-stream)))]
    in-stream))
