(ns coronavirus.interpolate
  (:require
   [incanter
    [charts :as charts]
    [core :as core]
    [interpolation :as interp]
    ]
   [coronavirus
    [csv :as csv]]
   )
  (:import java.text.SimpleDateFormat))

(def points
  #_[[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]]
  (mapv (fn [x y] [x y]) (range) (map :c (csv/get-counts))))

(def dates
  (map (fn [hm] (.parse (new SimpleDateFormat "MM-dd-yyyy")
                        (subs (:f hm) 0 10)))
       (csv/get-counts)))

(def degree
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

(defn plot [points fn]
  (-> (charts/parametric-plot fn 0 1
       :title
       #_(str "degree" degree)
       "@corona_cases_bot: interpolation - confirmed cases; see /about"
       :x-label "Jan12 + <day-number>" :y-label "Cases")
      (charts/add-points (map first points)
                         (map second points))
      #_(core/view)))

(defn interpolate-points [points]
  (interp/interpolate-parametric points :b-spline :degree degree))

(defn create-pic [points]
  (let [chart (plot points (interpolate-points points))
        out-stream (java.io.ByteArrayOutputStream.)
        in-stream (do
                    (core/save chart out-stream :width 800 :height 600)
                    (java.io.ByteArrayInputStream.
                     (.toByteArray out-stream)))]
    in-stream))
