(ns corona.macro)

(defmacro defn-fun-id
  "Docstring is mandatory!"
  [name docstr args & exprs]
  `(def ~name
     ~docstr
     (fn ~args (let [~'fun-id (str (quote ~name))]
                 ~@exprs))))

#_(defn-fun-id f "f-docs" [x]
  {:pre [(number? x)]}
  (printf "[%s] hi\n" fun-id)
  (inc x))

#_(defn-fun-id g "f-docs" [x]
  (printf "[%s] hi\n" fun-id)
  (inc (f x)))
