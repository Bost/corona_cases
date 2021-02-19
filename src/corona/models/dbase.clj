(ns corona.models.dbase
  (:require [next.jdbc :as jdbc]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre]
            [utils.core :as utc]))

(def dbname "postgres")

(def spec (or (System/getenv "DATABASE_URL")
              (format "postgresql://%s:%s@localhost:5432/%s"
                      "postgres"
                      #_(env :user)
                      "foo" ;; can be changed by \password
                      #_(env :password)
                      dbname)))

(def dbase-datasource
  (jdbc/get-datasource {:dbtype "postgresql"
                        :dbname dbname
                        :user "postgres"
                        :password "foo"}))

(comment
  (with-open [connection (jdbc/get-connection dbase-datasource)]
    (def table "chat")
    (def cs [:id :first_name :username :type])
    (defn vs [] [(rand-nth [1111111111 2222222222 3333333333]) "Jim" "Beam" "private"])

    ((comp
      (fn [cmd] (jdbc/execute-one! connection [cmd]))
      #_(reduce my-fn init-value (jdbc/plan connection [...]))
      (fn [cmd] (timbre/debugf "\n%s" cmd)
        cmd))
     (format "insert into %s (%s) values (%s)"
             (pr-str table)
             ((comp
               (fn [col] (utc/sjoin col ", "))
               (partial map name))
              cs)
             ((comp
               (fn [col] (utc/sjoin col ", "))
               (partial map (fn [v] (if (string? v) (format "'%s'" v) v))))
              (vs)))))

  (with-open [connection (jdbc/get-connection dbase-datasource)]
    (def table "chat")
    ((comp
      (fn [cmd] (jdbc/execute! connection [cmd]))
      #_(reduce my-fn init-value (jdbc/plan connection [...]))
      (fn [cmd] (timbre/debugf "\n%s" cmd)
        cmd))
     (format "select * from %s" (pr-str table)))))


(comment
  (with-open [connection (jdbc/get-connection dbase-datasource)]
    (def table "message")
    (def cs [:chat_id :id :date :text])
    (defn vs [] [(rand-nth [1111111111 2222222222 3333333333])
                 (rand-int 99)
                 "now()"
                 (rand-nth ["/start" "/world"])])
    ((comp
      (fn [cmd] (jdbc/execute-one! connection [cmd]))
      #_(reduce my-fn init-value (jdbc/plan connection [...]))
      (fn [cmd] (timbre/debugf "\n%s" cmd)
        cmd))
     (format "insert into %s (%s) values (%s)"
             (pr-str table)
             ((comp
               (fn [col] (utc/sjoin col ", "))
               (partial map name))
              cs)
             ((comp
               (fn [col] (utc/sjoin col ", "))
               (partial map (fn [v] (if (string? v) (format "'%s'" v) v))))
              (vs)))))

  (with-open [connection (jdbc/get-connection dbase-datasource)]
    (def table "message")
    ((comp
      (fn [cmd] (jdbc/execute! connection [cmd]))
      #_(reduce my-fn init-value (jdbc/plan connection [...]))
      (fn [cmd] (timbre/debugf "\n%s" cmd)
        cmd))
     (format "select * from %s" (pr-str table)))))


