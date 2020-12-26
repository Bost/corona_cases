(printf "Current-ns [%s] loading %s ...\n" *ns* 'corona.messages)

(ns corona.messages
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.string :as cstr]
   [corona.api.expdev07 :as data]
   [corona.common :as com]
   [corona.countries :as ccr]
   [corona.country-codes :as ccc]
   [corona.lang :as lang]
   [corona.plot :as plot]
   [morse.api :as morse]
   [utils.core :as utc :exclude [id]]
   [utils.num :as utn]
   [incanter.stats :as istats]
   ;; [incanter.zoo :as izoo]
   [taoensso.timbre :as timbre :refer [debugf
                                       ;; info infof warn errorf fatalf
                                       ]]))

;; (set! *warn-on-reflection* true)

(defn bot-name-formatted []
  (cstr/replace com/bot-name #"_" "\\\\_"))

(def ^:const options {:parse_mode com/markdown :disable_web_page_preview true})

(defn create-pred-hm [ccode] (data/create-pred-hm ccode))

(defn round-nr [value] (int (utn/round-precision value 0)))

(def ^:const max-diff-order-of-magnitude 7)

(defn pos-neg [n]
  (cond
    (pos? n) "📈"
    (neg? n) "📉"
    :else " "))

(defn plus-minus
  "Display \"+0\" when n is zero"
  [n]
  (com/left-pad
   (if (zero? n)
     "+0"
     (str
      #_(cond (pos? n) "↑"
              (neg? n) "↓"
              :else "")
      (cond (pos? n) "+"
            :else "")
      n
      #_(pos-neg n)))
   " " max-diff-order-of-magnitude))

(def ^:const padding-s
  "Stays constant" (+ 3 9))
(def ^:const padding-n
  "Count of digits to display. Increase it when the nr of cases. Increases by an
  order of magnitude" 8)

(defn format-linewise
  "
  line-fmt e.g.: \"%s: %s\n\"
  The count of parameters for every line must be equal to the count of \"%s\"
  format specifiers in the line-fmt and the count must be at least 1.
  Note that at the moment only \"%s\" is allowed.

  lines is a matrix of the following shape:
  [[fmt0 [v00 ... v0N]]
   ...
   [fmtM [vM0 ... vMN]]]
  E.g.:

  (format-linewise
   [[\"1. %s \\n\" [\"a\" 0]]
    [\"2. %s \\n\" [\"b\" 1]]
    [\"3. %s \\n\" [\"c\" 2]]]
   :line-fmt \"%s: %s\")

  fn-fmts is a fn of one arg - a vector of strings containing \"%s\"
  fn-args is a fn of one arg
  "
  [lines & {:keys [line-fmt fn-fmts fn-args]
            :or {line-fmt "%s"
                 fn-fmts identity
                 fn-args identity}}]
  {:pre [(let [cnt-fmt-specifiers (count (re-seq #"%s" line-fmt))]
           (and (pos? cnt-fmt-specifiers)
                (apply = cnt-fmt-specifiers
                       (map (fn [line] (count (second line))) lines))))]}
  (apply format
         (->> lines (map first) (fn-fmts) (reduce str))
         (map (fn [line] (apply format line-fmt
                               (fn-args (second line))))
              lines)))

;; The worldwide population has 3 commas:
;; (count (re-seq #"," (format "%+,2d" 7697236610))) ; => 3

(def blank
  #_" "
  #_"\u2004" ;; U+2004 	&#8196 	Three-Per-Em Space
  #_"\u2005" ;; U+2005 	&#8197 	Four-Per-Em Space
  "\u2006" ;; U+2006 	&#8198 	Six-Per-Em Space
  #_" " ;; U+2009 	&#8201 	Thin Space
  )

(def vline blank)

(def percent "%") ;; "\uFF05" "\uFE6A"

(defn fmt-to-cols
  "Info-message numbers of aligned to columns for better readability"
  [{:keys [emoji s n diff rate]}]
  (format
   #_(str "<code>%s" blank "%s" blank "%s" blank "%s</code>")
   (str "<code>%s" "</code>" vline
        "<code>" "%s" "</code>" vline
        "<code>" "%s" "</code>" vline
        "<code>" "%s</code>")
   (com/right-pad (str (if emoji emoji (str blank blank)) blank s)
                  blank padding-s)
   (com/left-pad n blank padding-n)
   (com/left-pad (if rate (str rate percent) blank) blank 3)
   (if (nil? diff)
     (com/left-pad "" blank max-diff-order-of-magnitude)
     (plus-minus diff))
   ))

#_(defn fmt-to-cols
    "Info-message numbers of aligned to columns for better readability"
    [{:keys [s n diff rate show-plus-minus]}]
    (format
     "<code>%s┃%s┃%s┃%s</code>"
   ;; "<code>%s┃%s %s</code>"
     (com/right-pad s " " padding-s)
     (let [formatted-n (format (if show-plus-minus "%+,2d" "%,2d") n)
           padded-n (com/left-pad
                     (format "%s%s"
                             (format "%s"
                                     (reduce str (repeat
                                                  (- 2 (count (re-seq #"," formatted-n)))
                                                  ",")))
                             formatted-n)
                     " " padding-n)]
       (str (.replaceAll padded-n "," " ")
            (if show-plus-minus
              (pos-neg n)
              "")))
     #_(com/left-pad (if rate (str rate "%") " ") " " 4)
     (format "%s%s"
             (com/left-pad (if (nil? diff)
                             ""
                             #_(format "%+d" diff)
                             (let [formatted-n (format "%+,2d" diff)
                                   padded-n (com/left-pad
                                             (format "%s%s"
                                                     (format "%s"
                                                             (reduce str (repeat
                                                                          (- 1 (count (re-seq #"," formatted-n)))
                                                                          ",")))
                                                     formatted-n)
                                             " "
                                             (- padding-n 2))]
                               (.replaceAll padded-n "," " ")))
                           " " max-diff-order-of-magnitude)
             (if (nil? diff)
               ""
               (pos-neg n)))
     ""
     #_(com/left-pad (if rate (str rate "%") " ") " " 4)))

(defn link [name url parse_mode]
  (if (= parse_mode com/html)
    (format "<a href=\"%s\">%s</a>" url name)
    (format "[%s](%s)" name url)))

(defn footer
  "Listing commands in the message footer correspond to the columns in the listing.
  See also `list-countries`, `bot-father-edit-cmds`."
  [parse_mode]
  (let [spacer "  "]
    (str
     ;; "Try" spacer
     (->> [lang/world lang/explain]
          (map com/encode-cmd)
          (map (fn [cmd] (com/encode-pseudo-cmd cmd parse_mode)))
          (cstr/join spacer))
     spacer
     "\n"
     ;; lang/listings ":  "
     (->> (mapv lang/list-sorted-by (into com/listing-cases-absolute
                                       com/listing-cases-per-100k))
          (map com/encode-cmd)
          (cstr/join spacer)))))

(defn reply-markup-btns [prm]
  {:reply_markup
   (json/write-str
    {:inline_keyboard
     [(reduce
       into
       (mapv (fn [type]
               (mapv (fn [case-kw]
                       {:text (str (get lang/buttons case-kw)
                                   (type lang/plot-type))
                        :callback_data (pr-str (assoc prm
                                                      :case-kw case-kw
                                                      :type type))})
                     com/absolute-cases))
             [:sum :abs]))]})})

(defn worldwide? [ccode]
  (utc/in? [ccc/worldwide-2-country-code ccc/worldwide-3-country-code
        ccc/worldwide] ccode))

(defn worldwide-plots
  ([prm] (worldwide-plots "worldwide-plots" prm))
  ([msg-id {:keys [data]}]
   (let [{ccode :cc
          chat-id :chat-id
          type :type
          case-kw :case-kw} (edn/read-string data)
         options (reply-markup-btns {:chat-id chat-id :cc ccode})
         content (let [plot-fn (if (= type :sum)
                                 plot/plot-sum plot/plot-absolute)]
                     ;; the plot is fetched from the cache, stats and report need not to be
                     ;; specified
                   (plot-fn case-kw))]
     (doall
      (morse/send-photo com/telegram-token chat-id options content))
     (debugf "[%s] send-photo: %s bytes sent" msg-id (count content)))))

;; (defn language [prm]
;;   (format
;;    "/lang:%s\n/lang:%s\n/lang:%s\n"
;;    "sk"
;;    "de"
;;    "en"
;;    (footer prm)))

(defn header [parse_mode pred-hm]
  (format
   (str
    "🗓 "
    (condp = parse_mode
      com/html "<b>%s</b>"
      ;; i.e. com/markdown
      "*%s*")
    " 🦠 @%s")
   (com/fmt-date (:t (data/last-report pred-hm)))
   (condp = parse_mode
     com/html com/bot-name
     ;; i.e. com/markdown
     (cstr/replace com/bot-name #"_" "\\\\_"))))

;; mapvals
;; https://clojurians.zulipchat.com/#narrow/stream/180378-slack-archive/topic/beginners/near/191238200

(defn- header-content [parse_mode]
  (let [ccode (ccr/get-country-code ccc/worldwide)
        pred_hm (create-pred-hm ccode)]
    (header parse_mode pred_hm)))

(defn calc-list-countries
  "Listing commands in the message footer correspond to the columns in the listing.
  See also `footer`, `bot-father-edit-cmds`."
  ([case-kw msg-idx prm] (calc-list-countries "calc-list-countries" case-kw msg-idx prm))
  ([msg-id case-kw msg-idx {:keys [cnt-msgs cnt-reports data parse_mode]}]
   (let [header-txt (header-content parse_mode)
         spacer " "
         sort-indicator "▴" ;; " " "▲"
         omag-active 7 ;; order of magnitude i.e. number of digits
         omag-recov  (inc omag-active)
         omag-deaths (dec omag-active)

         msg
         (format
          (format-linewise
           [["%s\n"   [header-txt]]
            ["%s\n"   [(format "%s %s;  %s/%s" lang/report cnt-reports msg-idx cnt-msgs)]]
            ["    %s " [(str lang/active    (if (= :a case-kw) sort-indicator " "))]]
            ["%s"     [spacer]]
            ["%s "    [(str lang/recovered (if (= :r case-kw) sort-indicator " "))]]
            ["%s"     [spacer]]
            ["%s\n"   [(str lang/deaths    (if (= :d case-kw) sort-indicator " "))]]
            ["%s"     [(str
                        "%s"   ; listing table
                        "%s"   ; sorted-by description; has its own new-line
                        "\n\n"
                        "%s"   ; footer
                        )]]])
          (cstr/join
           "\n"
           (map (fn [{:keys [a r d cc]}]
                  (let [ccode cc
                        cname (ccr/country-name-aliased ccode)]
                    (format "<code>%s%s%s%s%s %s</code>  %s"
                            (com/left-pad a " " omag-active)
                            spacer
                            (com/left-pad r " " omag-recov)
                            spacer
                            (com/left-pad d " " omag-deaths)
                            (com/right-pad cname 17)
                            (cstr/lower-case (com/encode-cmd ccode)))))
                (->> data
                     #_(take-last 11)
                     #_(partition-all 2)
                     #_(map (fn [part] (cstr/join "       " part))))))
          ""
          #_(if (= msg-idx cnt-msgs)
              (str "\n\n" (lang/list-sorted-by-desc case-kw))
              "")
          (footer parse_mode))]
     (debugf "[%s] case-kw %s msg-idx %s msg-size %s"
             msg-id case-kw msg-idx (count msg))
     msg)))

(defn get-from-cache! [case-kw ks fun msg-idx prm]
  (if prm
    (data/cache! (fn [] (fun case-kw msg-idx prm))
                 (conj ks (keyword (str msg-idx))))
    (vals (get-in @data/cache ks))))

(defn list-countries
  [case-kw & [msg-idx prm]]
  (get-from-cache! case-kw [:list :countries case-kw]
                   calc-list-countries msg-idx prm))

(defn calc-list-per-100k
  "Listing commands in the message footer correspond to the columns in the
  listing. See also `footer`, `bot-father-edit-cmds`."
  ([case-kw msg-idx prm] (calc-list-per-100k "calc-list-per-100k"
                                             case-kw msg-idx prm))
  ([msg-id case-kw msg-idx {:keys [cnt-msgs cnt-reports data parse_mode]}]
   (let [header-txt (header-content parse_mode)
         spacer " "
         sort-indicator "▴" ;; " " "▲"
         ;; omag - order of magnitude i.e. number of digits
         omag-active-per-100k 4
         omag-recove-per-100k omag-active-per-100k
         omag-deaths-per-100k (dec omag-active-per-100k)
         msg
         (format
          (format-linewise
           [["%s\n" [header-txt]]
            ["%s\n" [(format "%s %s;  %s/%s"
                             lang/report cnt-reports msg-idx cnt-msgs)]]
            ["%s "  [(str lang/active-per-1e5
                          (if (= :a100k case-kw) sort-indicator " "))]]
            ["%s"   [spacer]]
            ["%s "  [(str lang/recove-per-1e5
                          (if (= :r100k case-kw) sort-indicator " "))]]
            ["%s"   [spacer]]
            ["%s"   [(str lang/deaths-per-1e5
                          (if (= :d100k case-kw) sort-indicator " "))]]
            ["\n%s" [(str
                      "%s"     ; listing table
                      "%s"     ; sorted-by description; has its own new-line
                      "\n\n%s" ; footer
                      )]]])
          (cstr/join
           "\n"
           (map (fn [{:keys [a100k r100k d100k cc]}]
                  (let [ccode cc
                        cname (ccr/country-name-aliased ccode)]
                    (format "<code>   %s%s   %s%s    %s %s</code>  %s"
                            (com/left-pad a100k " " omag-active-per-100k)
                            spacer
                            (com/left-pad r100k " " omag-recove-per-100k)
                            spacer
                            (com/left-pad d100k " " omag-deaths-per-100k)
                            (com/right-pad cname 17)
                            (cstr/lower-case (com/encode-cmd ccode)))))
                (->> data
                     #_(take-last 11)
                     #_(partition-all 2)
                     #_(map (fn [part] (cstr/join "       " part))))))
          ""
          #_(if (= msg-idx cnt-msgs)
              (str "\n\n" (lang/list-sorted-by-desc case-kw))
              "")
          (footer parse_mode))]
     (debugf "[%s] case-kw %s msg-idx %s msg-size %s"
             msg-id case-kw msg-idx (count msg))
     msg)))

(defn list-per-100k
  [case-kw & [msg-idx prm]]
  (get-from-cache! case-kw [:list :100k case-kw]
                   calc-list-per-100k msg-idx prm))

(defn last-index-of
  "To find the last element in a collection the collection must be fully
  computed and so this can't be lazy."
  [coll elem]
  ;; (->> coll
  ;;      (map-indexed (fn [idx val] [idx val]))
  ;;      (filter (fn [indexed-el] (= elem (second indexed-el))))
  ;;      (last)
  ;;      (first))
  (first
   (transduce
    ;; the xform:
    (comp (map-indexed (fn [idx val] [idx val]))
          (filter (fn [indexed-el] (= elem (second indexed-el)))))
    ;; the reducer:
    (fn [& findings] (last findings))
    ;; the collection
    coll)))

(defn- confirmed-info
  [last-report pred-hm delta max-active-val max-active-date ccode cnt-countries]
  (let [{deaths          :d
         recove          :r
         active          :a
         active-per-100k :a100k
         recove-per-100k :r100k
         deaths-per-100k :d100k
         closed-per-100k :c100k
         a-rate          :a-rate
         r-rate          :r-rate
         d-rate          :d-rate
         c-rate          :c-rate ;; closed-rate
         } last-report
        active-last-8-reports (:a (data/last-8-reports pred-hm))
        [active-last-8th-report & active-last-7-reports] active-last-8-reports
        closed (+ deaths recove)
        {delta-deaths :d
         delta-recove :r
         delta-active :a
         delta-d100k  :d100k
         delta-r100k  :r100k
         delta-a100k  :a100k} delta
        delta-closed (+ delta-deaths delta-recove)
        active-last-7-avg (-> active-last-7-reports istats/mean round-nr)

        ;; ActC(t0)    = active(t0)    - active(t0-1d)
        ;; ActC(t0-1d) = active(t0-1d) - active(t0-2d)
        ;; ActC(t0-2d) = active(t0-2d) - active(t0-3d)
        ;; ActC(t0-3d) = active(t0-2d) - active(t0-4d)
        ;; ActC(t0-4d) = active(t0-2d) - active(t0-5d)
        ;; ActC(t0-5d) = active(t0-2d) - active(t0-6d)
        ;; ActC(t0-6d) = active(t0-6d) - active(t0-7d)

        ;; ActCL7CAvg =
        ;; = (ActC(t0)+ActC(t0-1d)+ActC+(t0-2d)+...+ActC(t0-6d)) / 7
        ;; = (active(t0) - active(t0-7d)) / 7
        active-change-last-7-avg (-> (/ (- active active-last-8th-report) 7.0)
                                     round-nr
                                     ;; plus-minus
                                     )]
    [["%s\n" [(fmt-to-cols
               {:emoji "🤒" :s lang/active :n active
                :diff delta-active :rate a-rate})]]
     ["%s\n" [(fmt-to-cols
               {:s lang/active-per-1e5 :n active-per-100k
                :diff delta-a100k})]]
     ["%s\n" [#_(fmt-to-cols
                 {:s lang/active-max
                  :n max-active-val})

              (format
               (str "<code>%s" "</code>" vline
                    "<code>" "%s" "</code>" vline
                    "(%s)")
               (com/right-pad (str (if nil nil (str blank blank)) blank
                                   lang/active-max) blank padding-s)
               (com/left-pad max-active-val blank padding-n)
               (com/fmt-date max-active-date))]]

     ;; TODO add effective reproduction number (R)
     #_["%s\n" [(fmt-to-cols
                 {:s lang/active-last-7-med
                  :n (->> active-last-7-reports (izoo/roll-median 7) (first)
                          (int))})]]
     ["%s\n" [(fmt-to-cols
               {:s lang/active-last-7-avg
                :n active-last-7-avg})]]
     ["%s\n" [(fmt-to-cols
               {:s lang/active-change-last-7-avg
                :n active-change-last-7-avg
                :show-plus-minus true})]]
     ["%s\n" [(fmt-to-cols
               {:emoji "🎉" :s lang/recovered :n recove
                :diff delta-recove :rate r-rate})]]
     ["%s\n" [(fmt-to-cols
               {:s lang/recove-per-1e5 :n recove-per-100k
                :diff delta-r100k})]]
     ["%s\n" [(fmt-to-cols
               {:emoji "⚰️" :s lang/deaths :n deaths
                :diff delta-deaths :rate d-rate})]]
     ["%s\n" [(fmt-to-cols
               {:s lang/deaths-per-1e5 :n deaths-per-100k
                :diff delta-d100k})]]
     ["%s\n" [(fmt-to-cols
               {:emoji "🏁" :s lang/closed :n closed
                :diff delta-closed :rate c-rate})]]
     ["%s\n\n" [(fmt-to-cols
                 {:s lang/closed-per-1e5 :n closed-per-100k
                  :diff delta-d100k
                  ;; TODO create command lang/cmd-closed-per-1e5
                  #_#_:desc (com/encode-cmd lang/cmd-closed-per-1e5)})]]
     ["%s\n" [(format
               #_"%s\n%s"
               "<code>%s</code>\n%s"
               #_"<code>%s\n%s</code>" lang/active-last-7
               (utc/sjoin active-last-7-reports))]]

               ;; no country ranking can be displayed for worldwide statistics
     (if (worldwide? ccode)
       ["" [""]]
       ["\n%s"
        [(format-linewise
          [["%s" [lang/people         :p]]
           ["%s" [lang/active-per-1e5 :a100k]]
           ["%s" [lang/recove-per-1e5 :r100k]]
           ["%s" [lang/deaths-per-1e5 :d100k]]
           ["%s" [lang/closed-per-1e5 :c100k]]]
          :line-fmt (str "<code>%s</code>: %s / " cnt-countries "\n")
          :fn-fmts
          (fn [fmts] (format lang/randking-desc
                             cnt-countries (cstr/join "" fmts)))
          :fn-args
          (fn [args] (update args (dec (count args))
                             (fn [_]
                               (get
                                (first
                                 (map :rank
                                      (filter (fn [{:keys [cc]}] (= cc ccode))
                                              (data/all-rankings))))
                                (last args))))))]])]))

(defn create-detailed-info-ext
  [{:keys
    [ccode pred-hm cnt-reports cnt-countries header-txt footer-txt
     max-active-val max-active-date last-report confirmed population
     population-rounded delta delta-confirmed country-name-aliased cc-c3-codes]}]
  (format-linewise
   [["%s\n"  ; extended header
     [(format-linewise
       [["%s  " [header-txt]]
        ["%s "  [country-name-aliased]]
        ["%s"   [;; country commands
                 (apply (fn [ccode c3code]
                          (format "     %s    %s" ccode c3code))
                        (map (fn [s] (com/encode-cmd (cstr/lower-case s)))
                             cc-c3-codes))]]])]]
    ["%s\n" [(str lang/report " " cnt-reports)]]
    ["%s\n" ; data
     [(format-linewise
       (apply
        conj
        [["%s\n" [(format "<code>%s %s</code> = %s %s"
                          (com/right-pad lang/people " " (- padding-s 2))
                          (com/left-pad population " " (+ padding-n 2))
                          population-rounded
                          lang/millions-rounded)]]
         ["%s\n" [(fmt-to-cols {:emoji "🦠" :s lang/confirmed :n confirmed
                                :diff delta-confirmed})]]]
        (when (pos? confirmed)
          (confirmed-info last-report pred-hm delta max-active-val
                          max-active-date ccode cnt-countries))))]]
    ["%s\n" [footer-txt]]]))

;; By default Vars are static, but Vars can be marked as dynamic to
;; allow per-thread bindings via the macro binding. Within each thread
;; they obey a stack discipline:
#_(def ^:dynamic points [[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]])
(defn create-detailed-info
  "Shows the table with the absolute and %-wise nr of cases, cases per-100k etc.
  TODO 3. show Case / Infection Fatality Rate (CFR / IFR)
  TODO Bayes' Theorem applied to PCR test: https://youtu.be/M8xlOm2wPAA
  (need 1. PCR-test accuracy, 2. Covid 19 disease prevalence)
  TODO create an API web service(s) for every field displayed in the messages
  "
  ([ccode parse_mode pred-hm]
   (create-detailed-info "create-detailed-info" ccode parse_mode pred-hm))
  ([msg-id ccode parse_mode pred-hm]
   ;; (debugf "[%s] ccode %s" msg-id ccode)
   ;; (debugf "[%s] parse_mode %s" msg-id parse_mode)
   ;; (debugf "[%s] pred-hm %s" msg-id pred-hm)
   (let [data-active (:a (data/case-counts-report-by-report pred-hm))
         max-active-val (apply max data-active)
         last-report (data/last-report pred-hm)
         delta (data/delta pred-hm)
         population (:p last-report)
         info (create-detailed-info-ext
               {:ccode ccode
                :pred-hm pred-hm
                :cnt-reports (count (data/dates))
                :cnt-countries (count ccc/all-country-codes)
                :header-txt (header parse_mode pred-hm)
                :footer-txt (footer parse_mode)
                :max-active-val max-active-val
                :max-active-date (nth (data/dates)
                                      (last-index-of data-active max-active-val))
                :last-report last-report
                :confirmed (:c last-report)
                :population population
                :population-rounded (utn/round-div-precision population 1e6 1)
                :delta delta
                :delta-confirmed (:c delta)
                :country-name-aliased (ccr/country-name-aliased ccode)
                :cc-c3-codes [ccode (ccc/country-code-3-letter ccode)]})]
     (debugf "[%s] ccode %s info-size %s" msg-id ccode (count info))
     info)))

(defn detailed-info
  [ccode & [parse_mode pred-hm]]
  (let [ks [:msg (keyword ccode)]]
    (if (and parse_mode pred-hm)
      (data/cache! (fn [] (create-detailed-info ccode parse_mode pred-hm))
                   ks)
      (get-in @data/cache ks))))

(defn feedback []
  (str "Just write a message to @RostislavSvoboda thanks."))

(defn contributors [parse_mode]
  (format "%s\n\n%s\n\n%s"
          (cstr/join "\n" ["@DerAnweiser"
                        (link "maty535" "https://github.com/maty535" parse_mode)
                        "@kostanjsek"
                        "@DistrictBC"
                        "Michael J."
                        "Johannes D."
                        ])
          (str
           "The rest of the contributors prefer anonymity or haven't "
           "approved their inclusion to this list yet. 🙏 Thanks folks.")
          (footer parse_mode)))

(defn explain [parse_mode]
  (str
   ;; escape underscores for the markdown parsing
   (bot-name-formatted)
   "🦠 @" com/botver " "
   (str
    (link "👩🏼‍💻 GitHub" "https://github.com/Bost/corona_cases" parse_mode) ", "
    (link "👨🏻‍💻 GitLab" "https://gitlab.com/rostislav.svoboda/corona_cases" parse_mode)
    "\n")
   "\n"
   (format "• %s %s = %s + %s\n"
           lang/closed lang/cases lang/recovered lang/deaths)
   (format "• %s: <%s> / %s\n" lang/percentage-calc lang/cases lang/confirmed)
   (format (str "• %s:\n"
                "  %s\n")
           lang/active-max
           (:doc (meta #'lang/active-max)))
   (format (str "• %s:\n"
                "  %s\n")
           lang/active-last-7
           (:doc (meta #'lang/active-last-7)))
   (format (str "• %s:\n"
                "  %s\n")
           lang/active-last-7-med
           (:doc (meta #'lang/active-last-7-med)))
   (format (str "• %s:\n"
                "  %s\n")
           lang/active-last-7-avg
           (:doc (meta #'lang/active-last-7-avg)))
   (format (str "• %s = (%s - %s) / 7\n"
                "  %s\n")
           lang/active-change-last-7-avg
           lang/active lang/active-last-8th
           (:doc (meta #'lang/active-change-last-7-avg)))
   ;; (abbreviated) content of the former reference message
   (format (str "• %s, %s, %s, %s:\n"
                "  %s\n")
           lang/active-per-1e5
           lang/recove-per-1e5
           lang/deaths-per-1e5
           lang/closed-per-1e5
           lang/cases-per-1e5)
   "\n"
   (format "🙏 Thanks goes to %s. Please ✍️ write %s\n"
           (com/encode-cmd lang/contributors)
           (com/encode-cmd lang/feedback))
   "\n"
   (footer parse_mode)))

(def ^:const bot-description
  "Keep it in sync with README.md"
  "Coronavirus disease 2019 (COVID-19) information on Telegram Messenger")

(defn bot-father-edit-description [] bot-description)
(defn bot-father-edit-about [] bot-description)
(defn bot-father-edit-inline-placeholder
  "Appears when a user types: @<botname>
  See https://core.telegram.org/bots/inline"
  [] "Coronavirus Information")

(printf "Current-ns [%s] loading %s ... done\n" *ns* 'corona.messages)
