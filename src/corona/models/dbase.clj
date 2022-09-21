;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.models.dbase)

(ns corona.models.dbase
  (:require
   [corona.models.common :as mcom]
   [corona.telemetry :refer [debugf defn-fun-id errorf]]
   [environ.core :refer [env]]
   [next.jdbc :as jdbc]
   [taoensso.timbre :as timbre]
   [utils.core :as utc]))

(defn-fun-id chat-exists? "" [{:keys [id]}]
  (let [table "chat"
        exists-kw :exists]
    (with-open [connection (jdbc/get-connection mcom/datasource)]
      ((comp
        exists-kw
        first
        (fn [result] (debugf "result:\n  %s" result) result)
        (fn [cmd] (jdbc/execute! connection [cmd id]))
        #_(reduce my-fn init-value (jdbc/plan connection [...]))
        (fn [cmd] (debugf "\n%s" cmd) cmd))
       (format "select %s(select 1 from %s where id = ?)"
               (name exists-kw)
               (pr-str table))))))

;; TODO test insert into chat table
(def insert {:type :insert :cmd "insert into %s (%s) values (%s)"})
(def upsert
  {:type :upsert :cmd
   "insert into %s (%s) values (%s) on conflict (%s) do update set %s"
   })

(defn col-names [cols]
  ((comp
    (fn [col] (utc/sjoin col ", "))
    (partial map name))
   cols))

(defn col-vals [cols]
  ((comp
    (fn [cols] (utc/sjoin cols ", "))
    (partial map (fn [col]
                   (cond
                     (= :type col) "cast(? as chat_type)"
                     (utc/in? [:created_at :updated_at] col) "cast(? as timestamp(0))"
                     :else "?"))))
   cols))

;; TODO create spec against the :else branch
(defn-fun-id prepate-statement ""
  [{:keys [type cmd conflict-col set-cmd] :as crud-cmd} table cols]
  (cond
    (= type :upsert)
    (format cmd
            (pr-str table)
            (col-names cols)
            (col-vals cols)
            conflict-col
            set-cmd)

    (= type :insert)
    (format cmd
            (pr-str table)
            (col-names cols)
            (col-vals cols))
    :else
    (throw (Exception. (format "Unknown type of %s" crud-cmd)))))

(defn-fun-id insert-chat! "" [{:keys [id first_name username type]}]
  (with-open [connection (jdbc/get-connection mcom/datasource)]
    (let [table "chat"
          cols [:id :first_name :username :type :created_at]
          prep-stmt (prepate-statement insert table cols)]
      ((comp
        (fn [result] (debugf "result:\n    %s" result) result)
        (fn [cmd] (try (jdbc/execute-one! connection cmd)
                       (catch Exception e
                         (errorf "Caught %s" e))))
        (fn [cmd] (debugf "cmd %s" cmd) cmd)
        (partial into [prep-stmt]))
       #_[(rand-nth [1111111111 2222222222 3333333333])
          "Jim" "Beam" "private" "now()"]
       [id first_name username type "now()"]))))

(defn-fun-id upsert-threshold!
  "(upsert-threshold! {:kw kvac :inc (int 1e6) :val (int 1e7)})"
  [{:keys [kw inc val] :as prm}]
  (with-open [connection (jdbc/get-connection mcom/datasource)]
    (let [table "thresholds"
          cols [:kw :inc :val :updated_at]
          prep-stmt (prepate-statement
                     (assoc upsert
                            :conflict-col (name :kw)
                            :set-cmd (str
                                      (name :val) " = " val
                                      ", "
                                      (name :updated_at) " = cast('now()' as timestamp(0))"
                                      ))
                     table cols)]
      (debugf "prep-stmt:\n  %s" prep-stmt)
      ((comp
        (fn [result] (debugf "result:\n    %s" result) result)
        (fn [cmd] (try (jdbc/execute-one! connection cmd)
                      (catch Exception e
                        (errorf "Caught %s" e))))
        (fn [cmd] (debugf "cmd:\n  %s" cmd) cmd)
        (partial into [prep-stmt])
        (partial map (fn [v] (if (keyword? v) (name v) v))))
       #_[kvac (int 1e6) (int 1e7) "now()"]
       [kw inc val "now()"]))))

(defn-fun-id get-thresholds "" []
  (with-open [connection (jdbc/get-connection mcom/datasource)]
    (let [table "thresholds"]
      ((comp
        (fn [result] (debugf "result:\n  %s" result) result)
        (fn [cmd] (jdbc/execute! connection [cmd]))
        #_(reduce my-fn init-value (jdbc/plan connection [...]))
        (fn [cmd] (debugf "cmd:\n  %s" cmd)
          cmd))
       (format "select * from %s" (pr-str table))))))

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
