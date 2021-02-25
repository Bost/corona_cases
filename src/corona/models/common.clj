;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.models.common)

(ns corona.models.common
  (:require [next.jdbc :as jdbc]
            [corona.common :as com]))

(def datasource (jdbc/get-datasource com/dbase))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.models.common)
