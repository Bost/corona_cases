(
 ;; https://www.gnu.org/software/emacs/manual/html_node/emacs/Rebinding.html
 ;; see local-function-key-map
 ;; `M-x local-set-key RET key cmd RET' seems not to be permanent
 (nil . ((eval . (setq corona-home (format "%s/dec/corona_cases" (getenv "HOME"))))
         (eval . (set-local-keymap (kbd "<s-f5>")
                                   '(find-file (format "%s/src/corona/api/v1.clj" corona-home))))
         (eval . (set-local-keymap (kbd "<s-f6>")
                                   '(find-file (format "%s/src/corona/telegram.clj" corona-home))))
         (eval . (set-local-keymap (kbd "<s-f7>")
                                   '(find-file (format "%s/src/corona/msg/text/details.clj" corona-home))))))
 (clojure-mode
  .
  ((cider-preferred-build-tool . clojure-cli)
   ;; (cider-clojure-cli-parameters . "-J-Djdk.attach.allowAttachSelf")
   ;; -Xmx<size> - keep in sync with Procfile
   (cider-clojure-cli-global-options
    .
    "-J-Xmx1024m -J-XX:+HeapDumpOnOutOfMemoryError -J-Djdk.attach.allowAttachSelf -J--illegal-access=permit"
    ))))
