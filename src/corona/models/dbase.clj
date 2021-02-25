;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.models.dbase)

(ns corona.models.dbase
  (:require [next.jdbc :as jdbc]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre]
            [corona.macro :refer [defn-fun-id debugf]]
            [corona.models.common :as mcom]
            [utils.core :as utc]))

(defn-fun-id ok?
  "TODO Check dbase connection and dbase consistency"
  []
  (with-open [connection
              (jdbc/get-connection mcom/datasource)]
    (debugf "connection %s" connection)
    (def table1 "chat")
    #_(def table2 "messages")
    ((comp
      (fn [rows] (-> rows first :exists))
      (fn [cmd] (jdbc/execute! connection [cmd table1]))
      #_(reduce my-fn init-value (jdbc/plan connection [...]))
      (fn [cmd] (debugf "\n%s" cmd)
        cmd))
     (format "
select exists(
  select 1 from information_schema.tables where table_name in (?)
)"))))

(defn-fun-id chat-exists? "" [{:keys [chat-id]}]
  (let [table "chat"
        exists-kw :exists]
    (with-open [connection
                ((comp
                  (fn [cmd] (debugf "\n%s" cmd) cmd)
                  jdbc/get-connection
                  (fn [cmd] (debugf "\n%s" cmd) cmd))
                 mcom/datasource)]
      ((comp
        (fn [rows] (-> rows first exists-kw))
        (fn [cmd] (jdbc/execute! connection [cmd chat-id]))
        #_(reduce my-fn init-value (jdbc/plan connection [...]))
        (fn [cmd] (debugf "\n%s" cmd) cmd))
       (format "select %s(select 1 from %s where id = ?)" (name exists-kw) (pr-str table))))))

(comment
  (with-open [connection (jdbc/get-connection mcom/datasource)]
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

  (with-open [connection (jdbc/get-connection mcom/datasource)]
    (def table "chat")
    ((comp
      (fn [cmd] (jdbc/execute! connection [cmd]))
      #_(reduce my-fn init-value (jdbc/plan connection [...]))
      (fn [cmd] (timbre/debugf "\n%s" cmd)
        cmd))
     (format "select * from %s" (pr-str table)))))


(comment
  (with-open [connection (jdbc/get-connection mcom/datasource)]
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

  (with-open [connection (jdbc/get-connection mcom/datasource)]
    (def table "message")
    ((comp
      (fn [cmd] (jdbc/execute! connection [cmd]))
      #_(reduce my-fn init-value (jdbc/plan connection [...]))
      (fn [cmd] (timbre/debugf "\n%s" cmd)
        cmd))
     (format "select * from %s" (pr-str table)))))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.models.dbase)
