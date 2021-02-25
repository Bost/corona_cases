;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.models.migration)

(ns corona.models.migration)

;; (ns corona.models.migration
;;   (:require [clojure.java.jdbc :as jdbc]
;;             [corona.models.dbase :as dbase]))

#_(defn migrated? []
  (-> (sql/query dbase/spec
                 [(format (str "select count(*) from information_schema.tables "
                               "where table_name='%s'")
                          dbase/dbname)])
      first :count pos?))

#_(defn migrate []
  (when (not (migrated?))
    (print "Creating database structure...") (flush)
    (sql/db-do-commands dbase/spec
                        (sql/create-table-ddl
                         :shouts
                         [[:id :serial "PRIMARY KEY"]
                          [:body :varchar "NOT NULL"]
                          [:created_at :timestamp
                           "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"]]))
    (println " done")))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.models.migration)
