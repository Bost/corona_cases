((clojure-mode
  .
  ((cider-preferred-build-tool . clojure-cli)
   ;; (cider-clojure-cli-parameters . "-J-Djdk.attach.allowAttachSelf")
   ;; -Xmx<size> - keep in sync with Procfile
   (cider-clojure-cli-global-options
    .
    "-J-Xmx1000m -J-XX:+HeapDumpOnOutOfMemoryError -J-Djdk.attach.allowAttachSelf"))))
