(ns corona.models.migration
  (:require [clojure.java.jdbc :as sql]
            [corona.models.dbase :as dbase]))

(defn migrated? []
  (-> (sql/query dbase/spec
                 [(str "select count(*) from information_schema.tables "
                       "where table_name='shouts'")])
      first :count pos?))

(defn migrate []
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
