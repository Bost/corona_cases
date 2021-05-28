(
 ;; https://www.gnu.org/software/emacs/manual/html_node/emacs/Rebinding.html
 ;; see local-function-key-map
 ;; `M-x local-set-key RET key cmd RET' seems not to be permanent
 (nil
  .
  ((eval
    .
    (progn
      (setq-local my=corona-home (format "%s/dec/corona_cases" (getenv "HOME")))
      (set-local-keymap
       (kbd "<s-f4>") '(find-file
                        (format "%s/src/corona/estimate.clj" my=corona-home)))
      (set-local-keymap
       (kbd "<s-f5>") '(find-file
                        (format "%s/src/corona/api/v1.clj" my=corona-home)))
      (set-local-keymap
       (kbd "<s-f6>") '(find-file
                        (format "%s/src/corona/telegram.clj" my=corona-home)))
      (set-local-keymap
       (kbd "<s-f7>") '(find-file
                        (format "%s/src/corona/msg/text/details.clj"
                                my=corona-home)))
      (set-local-keymap
       (kbd "<s-f10>") '(progn
                          (find-file
                           (format "%s/src/corona/country_codes.clj"
                                   my=corona-home))
                          ;; ((commandp 'dumb-jump-go)
                          ;;  (call-interactively #'dumb-jump-go))
                          (xref-find-definitions "all-country-codes")))))))
 (clojure-mode
  .
  ((eval
    .
    (progn
      (setq cider-clojure-cli-global-options
            ;; -J is for /usr/local/bin/clojure
            (format "-J%s -J%s -J%s -J%s"
                    "-Xmx1024m" ;; keep the value in sync with the Procfile
                    "-XX:+HeapDumpOnOutOfMemoryError"
                    "-Djdk.attach.allowAttachSelf"
                    "--illegal-access=permit"))
      '((cider-preferred-build-tool . clojure-cli)))))))
