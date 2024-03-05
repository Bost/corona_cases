;; (printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.models.dbase)

(ns corona.models.dbase
  (:require
   [corona.common :as com]
   [corona.models.common :as mcom]
   [corona.utils.core :as cutc]
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

;; TODO: test insert into chat table
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

;; TODO: create spec against the :else branch
(defn-fun-id prepare-statement ""
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

(let [table "chat"]
  (defn-fun-id insert-chat! "" [{:keys [id first_name username type]}]
    (with-open [connection (jdbc/get-connection mcom/datasource)]
      (let [cols [:id :first_name :username :type :created_at]
            prep-stmt (prepare-statement insert table cols)]
        ((comp
          (fn [result] (debugf "result:\n    %s" result) result)
          (fn [cmd] (try (jdbc/execute-one! connection cmd)
                         (catch Exception e
                           (errorf "Caught %s" e))))
          (fn [cmd] (debugf "cmd:\n  %s" cmd) cmd)
          (partial into [prep-stmt]))
         #_[(rand-nth [1111111111 2222222222 3333333333])
            "Jim" "Beam" "private" "now()"]
         [id first_name username type "now()"])))))

(let [table "thresholds"]
  (defn-fun-id upsert-threshold!
    "(upsert-threshold! {:kw kvac :inc (int 1e6) :val (int 1e7)})"
    [{:keys [kw inc val] :as prm}]
    (with-open [connection (jdbc/get-connection mcom/datasource)]
      (let [cols [:kw :inc :val :updated_at]
            prep-stmt (prepare-statement
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

  (defn-fun-id get-thresholds
    "E.g.:
  (get-thresholds)"
    []
    (with-open [connection (jdbc/get-connection mcom/datasource)]
      ((comp
        (fn [coll]
          (let [sep "  "]
            ((comp
              (fn [coll] (debugf "result:\n%s%s" sep
                                 ;; TODO: make utc/sjoin flexible
                                 (utc/sjoin coll (str "\n" sep))))
              (partial map ;; tidy up for logging
                       (comp
                        (partial cutc/select-keys
                                 :ks [:kw :val :updated_at])
                        (partial
                         cutc/update-in
                         :ks [:updated_at]
                         :f (fn [date]
                              (.format com/updated_at-date-format date))))))
             coll))
          coll)
        ;; tidy up the result for consumption outside of this function
        (partial map (comp (partial cutc/update-in :ks [:kw] :f keyword)
                           (partial cutc/rename-keys
                                    :km {:thresholds/kw :kw
                                         :thresholds/inc :inc
                                         :thresholds/val :val
                                         :thresholds/updated_at :updated_at})))
        (fn [cmd] (jdbc/execute! connection [cmd]))
        #_(reduce my-fn init-value (jdbc/plan connection [...]))
        (fn [cmd] (debugf "cmd:\n  %s" cmd)
          cmd))
       (format "select * from %s" (pr-str table))))))

(let [table "last_calculations"]
  (defn-fun-id upsert-last-calculations!
    "E.g.:
(upsert-last-calculations! {:kw \"estim\" :val \"...\"})"
    [{:keys [kw val] :as prm}]
    (with-open [connection (jdbc/get-connection mcom/datasource)]
      (let [cols [:kw :val :updated_at]
            prep-stmt (prepare-statement
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
         [kw val "now()"]))))

  (defn-fun-id get-last-calculations
    "E.g.:
  (get-last-calculations)"
    []
    (with-open [connection (jdbc/get-connection mcom/datasource)]
      ((comp
        (fn [coll]
          (let [sep "  "]
            ((comp
              (fn [coll] (debugf "result:\n%s%s" sep
                                 ;; TODO: make utc/sjoin flexible
                                 (utc/sjoin coll (str "\n" sep))))
              (partial map ;; tidy up for logging
                       (comp
                        (partial cutc/select-keys
                                 :ks [:kw :val :updated_at])
                        (partial
                         cutc/update-in
                         :ks [:updated_at]
                         :f (fn [date]
                              (.format com/updated_at-date-format date))))))
             coll))
          coll)
        ;; tidy up the result for consumption outside of this function
        (partial map (comp (partial cutc/update-in :ks [:kw] :f keyword)
                           (partial cutc/rename-keys
                                    :km {:thresholds/kw :kw
                                         :thresholds/val :val
                                         :thresholds/updated_at :updated_at})))
        (fn [cmd] (jdbc/execute! connection [cmd]))
        #_(reduce my-fn init-value (jdbc/plan connection [...]))
        (fn [cmd] (debugf "cmd:\n  %s" cmd)
          cmd))
       (format "select * from %s" (pr-str table))))))

(comment
  (with-open [connection (jdbc/get-connection mcom/datasource)]
    ((comp
      (fn [cmd] (jdbc/execute! connection [cmd]))
      #_(reduce my-fn init-value (jdbc/plan connection [...]))
      (fn [cmd] (timbre/debugf "\n%s" cmd)
        cmd))
     (format "select * from %s" (pr-str "chat")))))

(comment
  (with-open [connection (jdbc/get-connection mcom/datasource)]
    ((comp
      (fn [cmd] (jdbc/execute-one! connection [cmd]))
      #_(reduce my-fn init-value (jdbc/plan connection [...]))
      (fn [cmd] (timbre/debugf "\n%s" cmd)
        cmd))
     (format "insert into %s (%s) values (%s)"
             (pr-str "message")
             ((comp
               (fn [col] (utc/sjoin col ", "))
               (partial map name))
              [:chat_id :id :date :text])
             ((comp
               (fn [col] (utc/sjoin col ", "))
               (partial map (fn [v] (if (string? v) (format "'%s'" v) v))))
              [(rand-nth [1111111111 2222222222 3333333333])
               (rand-int 99)
               "now()"
               (rand-nth ["/start" "/world"])]))))

  (with-open [connection (jdbc/get-connection mcom/datasource)]
    ((comp
      (fn [cmd] (jdbc/execute! connection [cmd]))
      #_(reduce my-fn init-value (jdbc/plan connection [...]))
      (fn [cmd] (timbre/debugf "\n%s" cmd)
        cmd))
     (format "select * from %s" (pr-str "message")))))

;; (printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.models.dbase)
