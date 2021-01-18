(ns corona.models.dbase
  (:require [clojure.java.jdbc :as sql]
            [environ.core :refer [env]]))

(def spec (or (System/getenv "DATABASE_URL")
              (format "postgresql://%s:%s@localhost:5432/survey"
                      (env :user)
                      (env :user))))

(defn all []
  (into [] (sql/query spec ["select * from shouts order by id desc"])))

(defn create [text]
  (sql/insert! spec :shouts [:body] [text]))
