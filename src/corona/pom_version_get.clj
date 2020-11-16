(printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.pom-version-get)

(ns corona.pom-version-get
    "Thanks to Wilker Lucio
See https://github.com/borkdude/babashka/blob/master/examples/pom_version_get.clj"
  (:require
   [clojure.data.xml]
   [clojure.string]))

(defn tag-name? [tag tname]
  (some-> tag :tag name #{tname}))

(defn tag-content-str [tag]
  (->> tag :content (filter string?) (clojure.string/join "")))

(defn parse-xml-str [xml]
  (->>
   xml
   (clojure.data.xml/parse-str)
   (xml-seq)
   (filter #(tag-name? % "version"))
   first
   tag-content-str))

(printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.pom-version-get)
