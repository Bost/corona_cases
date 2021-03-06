;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.models.dbase)

(ns corona.models.dbase
  (:require [next.jdbc :as jdbc]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre]
            [corona.macro :refer [defn-fun-id debugf errorf]]
            [corona.models.common :as mcom]
            [utils.core :as utc]))

(defn-fun-id chat-exists? "" [{:keys [id]}]
  (let [table "chat"
        exists-kw :exists]
    (with-open [connection (jdbc/get-connection mcom/datasource)]
      ((comp
        (fn [rows] (-> rows first exists-kw))
        (fn [cmd] (jdbc/execute! connection [cmd id]))
        #_(reduce my-fn init-value (jdbc/plan connection [...]))
        (fn [cmd] (debugf "\n%s" cmd) cmd))
       (format "select %s(select 1 from %s where id = ?)"
               (name exists-kw)
               (pr-str table))))))

(defn prepate-statement [table cols]
  (format "insert into %s (%s) values (%s)"
          (pr-str table)
          ((comp
            (fn [col] (utc/sjoin col ", "))
            (partial map name))
           cols)
          ((comp
            (fn [cols] (utc/sjoin cols ", "))
            (partial map (fn [col]
                           (cond
                             (= :type col) "cast(? as chat_type)"
                             (= :created_at col) "cast(? as timestamp(0))"
                             :else "?"))))
           cols)))

(defn-fun-id insert-chat! "" [{:keys [id first_name username type] :as prm}]
  (with-open [connection (jdbc/get-connection mcom/datasource)]
    (let [table "chat"
          cols [:id :first_name :username :type :created_at]
          prep-stmt (prepate-statement table cols)]
      ((comp
        (fn [res] (debugf "res: %s" res) res)
        (fn [cmd] (try (jdbc/execute-one! connection cmd)
                      (catch Exception e
                        (errorf "Caught %s" e))))
        (fn [cmd] (debugf "cmd %s" cmd) cmd)
        (partial into [prep-stmt]))
       #_[(rand-nth [1111111111 2222222222 3333333333])
          "Jim" "Beam" "private" "now()"]
       [id first_name username type "now()"]))))

(comment
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
