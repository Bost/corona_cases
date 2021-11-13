;; To activate changes switch to any clojure buffer and reload clojure-mode:
;;   M-x cider-switch-to-last-clojure-buffer
;;   M-x clojure-mode
;; then switch to a REPL-buffer and reload the cider-repl-mode:
;;   M-x cider-switch-to-repl-buffer
;;   M-x cider-repl-mode
(
 ;; https://www.gnu.org/software/emacs/manual/html_node/emacs/Rebinding.html
 ;; see local-function-key-map
 ;; `M-x local-set-key RET key cmd RET' seems not to be permanent
 (nil
  .
  ((eval
    .
    (progn
      (defun corona=cider-browse-all-ns-corona ()
        (interactive)
        (my=cider-browse-all-ns "corona"))

      (defun corona=cider-unmap-all-ns-corona ()
        (interactive)
        (my=cider-unmap-all-ns "corona"))

      ;; "corona.bot"
      (setq-local bot-ns "corona")
      (defun corona=telegram-restart ()
        (interactive)
        ;; (cider-switch-to-repl-buffer)
        ;; (cider-switch-to-last-clojure-buffer)
        (cider-ns-refresh)
        (my=repl-insert-cmd
         (format
          (concat
           "(System/gc)"
           " (swap! %s.api.cache/cache (fn [_])) (%s.telegram/restart)")
          bot-ns bot-ns)))

      (defun corona=web-restart ()
        (interactive)
        (my=repl-insert-cmd (format "(%s.web.core/webapp-restart)" bot-ns)))

      (defun corona=show-pic ()
        (interactive)
        (let ((ns (format "%s.msg.graph.plot" bot-ns))
              (case ":a"))
          (my=cider-insert-and-format
           `(
             "(cljplot.core/show"
             ,(format " (%s/aggregation-img" ns)
             ,(format "  %s/thresholds" ns)
             ,(format "  %s/stats-old" ns)
             ,(format "  %s/stats-new" ns)
             ,(format "  %s/last-date" ns)
             ,(format "  %s/cnt-reports" ns)
             ,(format "  %s/aggregation-kw" ns)
             ,(format "  %s/case-kw))" ns)))))

      (defun corona=show-pic-for-pred ()
        (interactive)
        (my=repl-insert-cmd
         (format
          (concat
           "(cljplot.core/show (%s.msg.graph.plot/message-img \"ZZ\""
           " %s.msg.graph.plot/stats %s.msg.graph.plot/report))")
          bot-ns bot-ns bot-ns bot-ns)))

      (setq-local home-dir (format "%s/dec/corona_cases" (getenv "HOME")))

      (defun corona=find-file--estimate.clj ()
        (interactive)
        (find-file
         (format "%s/src/corona/estimate.clj" home-dir)))

      (defun corona=find-file--v1.clj ()
        (interactive)
        (find-file
         (format "%s/src/corona/api/v1.clj" home-dir)))

      (defun corona=find-file--telegram.clj ()
        (interactive)
        (find-file
         (format "%s/src/corona/telegram.clj" home-dir)))

      (defun corona=find-file--details.clj ()
        (interactive)
        (find-file
         (format "%s/src/corona/msg/text/details.clj"
                 home-dir)))

      (defun corona=find-file--cases.clj ()
        (interactive)
        (progn
          (find-file
           (format "%s/src/corona/cases.clj"
                   home-dir))))

      (defun corona=find-file--country_codes.clj ()
        (interactive)
        (progn
          (find-file
           (format "%s/src/corona/country_codes.clj"
                   home-dir))
          ;; ((commandp 'dumb-jump-go)
          ;;  (call-interactively #'dumb-jump-go))
          (xref-find-definitions "all-country-codes")))

      (dolist (state-map `(,clojure-mode-map ,cider-repl-mode-map))
        ;; See also `set-local-keymap'
        (bind-keys
         :map state-map
         ;; The binding description doesn't appear in the `M-x helm-descbinds'
         ;; if the binding is defined using lambda:
         ;;    ("<some-key>" . (lambda () (interactive) ...))
         ("<f5>"    . corona=telegram-restart)
         ("<f6>"    . corona=web-restart)
         ("<f7>"    . corona=show-pic)
         ("<f8>"    . corona=show-pic-for-pred)
         ("<s-f4>"  . corona=find-file--estimate.clj)
         ("<s-f5>"  . corona=find-file--v1.clj)
         ("<s-f6>"  . corona=find-file--telegram.clj)
         ("<s-f7>"  . corona=find-file--details.clj)
         ("<s-f8>"  . corona=find-file--cases.clj)
         ;; ("<s-f9>"  . corona=find-file--.clj)
         ("<s-f10>" . corona=find-file--country_codes.clj)))))))
 (clojure-mode
  .
  ((eval
    .
    (progn
      (setq cider-clojure-cli-global-options
            ;; -J is for /usr/local/bin/clojure
            (format "-J%s -J%s -J%s -J%s -J%s"
                    ;; keep the -Xmx value in sync with the Procfile
                    (format "-Xmx%sm" (* 8 1024))
                    "-XX:+HeapDumpOnOutOfMemoryError"
                    "-Djdk.attach.allowAttachSelf"
                    "--illegal-access=permit"
                    "-XX:-OmitStackTraceInFastThrow")
            ;; (format "-J%s -J%s -J%s"
            ;;         "-Xmx1024m" ;; keep the value in sync with the Procfile
            ;;         "-XX:+HeapDumpOnOutOfMemoryError"
            ;;         "--illegal-access=permit")
            )
      '((cider-preferred-build-tool . clojure-cli)))))))
