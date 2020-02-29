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

(defn plot [points fn]
  (-> (charts/function-plot
       fn 0 (count points)
       :title "@corona_cases_bot: interpolation - confirmed cases; see /about"
       :x-label "Jan12 + <day-number>" :y-label "Cases")
      (charts/add-points (map first points)
                         (map second points))
      #_(core/view)))

(defn interpolate-points [points]
  (interp/interpolate points :linear-least-squares))

(defn create-pic [points]
  (let [chart (plot points (interpolate-points points))
        out-stream (java.io.ByteArrayOutputStream.)
        in-stream (do
                    (core/save chart out-stream :width 800 :height 600)
                    (java.io.ByteArrayInputStream.
                     (.toByteArray out-stream)))]
    in-stream))
