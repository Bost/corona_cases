(ns morse.polling-patch
  (:require [morse.polling :as p]
            [clojure.tools.logging :as log]
            [clojure.core.async :as a
             :refer [chan go go-loop thread
                     >!! >! <! close! alts!]]
            [morse.api :as api]
            ))

(defn create-producer-with-handle
  "Passed channel should be always empty.
   Close it to stop long-polling.
   Returns channel with updates from Telegram"
  [running token opts api-error-handle-fn]
  (let [updates (a/chan)
        ;; timeout for Telegram API in seconds
        timeout (or (:timeout opts) 1)]
    (go-loop [offset 0]
      (let [;; fix for JDK bug https://bugs.openjdk.java.net/browse/JDK-8075484
            ;; introduce additional timeout 10 times more that telegram's one
            wait-timeout (a/go (a/<! (a/timeout (* 1000 timeout 10)))
                               ::wait-timeout)
            response     (api/get-updates-async token (assoc opts :offset offset))
            [data _] (a/alts! [running response wait-timeout])]
        (case data
          ;; running got closed by the user
          nil
          (do (log/info "Stopping Telegram polling...")
              (close! wait-timeout)
              (close! updates))

          ::wait-timeout
          (do (log/error "HTTP request timed out, stopping polling")
              (close! running)
              (close! updates)
              (log/fatal "ABORT on ::wait-timeout")
              (System/exit 1))

          ::api/error
          (do (log/warn "Got error from Telegram API, stopping polling")
              (close! running)
              (close! updates)
              (log/fatal "ABORT on ::api/error")
              (api-error-handle-fn))

          (do (close! wait-timeout)
              (doseq [upd data] (>! updates upd))
              (recur (p/new-offset data offset))))))
    updates))

