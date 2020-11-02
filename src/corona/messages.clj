(printf "Current-ns [%s] loading %s\n" *ns* 'corona.messages)

(ns corona.messages
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.string :as s]
   [corona.api.expdev07 :as data]
   [corona.common :as com]
   [corona.countries :as ccr]
   [corona.country-codes :as ccc]
   [corona.lang :as l]
   [corona.plot :as p]
   [morse.api :as morse]
   [utils.core :as u :refer [in?] :exclude [id]]
   [utils.num :as utn]
   [incanter.stats :as istats]
   [incanter.zoo :as izoo]
   [taoensso.timbre :as timbre :refer [debugf
                                       ;; info infof warn errorf fatalf
                                       ]]
   ))

;; (set! *warn-on-reflection* true)

(defn bot-name-formatted []
  (s/replace com/bot-name #"_" "\\\\_"))

(def ^:const options {:parse_mode "Markdown" :disable_web_page_preview true})

(defn create-pred-hm [ccode] (data/create-pred-hm ccode))

(defn round-nr [value] (int (utn/round-precision value 0)))

(def ^:const max-diff-order-of-magnitude 7)

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
      n))
   " " max-diff-order-of-magnitude))

(def ^:const padding-s
  "Stays constant" 9)
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

(defn fmt-to-cols
  "Info-message numbers of aligned to columns for better readability"
  [{:keys [s n diff calc-diff rate]
    :or {calc-diff true}}]
  (format
   "<code>%s %s %s %s </code>"
   (com/right-pad s " " padding-s)
   (com/left-pad n " " padding-n)
   (com/left-pad (if rate (str rate "%") " ") " " 4)
   (if calc-diff
     (plus-minus diff)
     (com/left-pad "" " " max-diff-order-of-magnitude))))

(def ^:const ref-mortality-rate
  "https://www.worldometers.info/coronavirus/coronavirus-death-rate/")

(def ^:const ref-rober-koch
  "https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/nCoV.html")

(def ^:const ref-3blue1brown-exp-growth
  "https://youtu.be/Kas0tIxDvrg")

(def ^:const ref-age-sex
  "https://www.worldometers.info/coronavirus/coronavirus-age-sex-demographics/")

(defn link [name url parse_mode]
  (if (= parse_mode "HTML")
    (format "<a href=\"%s\">%s</a>" url name)
    (format "[%s](%s)" name url)))

(defn footer
  "Listing commands in the message footer correspond to the columns in the listing.
  See also `list-countries`, `bot-father-edit-cmds`."
  [parse_mode]
  (let [spacer "  "]
    (str
     ;; "Try" spacer
     (->> [l/world l/explain]
          (map com/encode-cmd)
          (map (fn [cmd] (com/encode-pseudo-cmd cmd parse_mode)))
          (s/join spacer))
     spacer
     "\n"
     ;; l/listings ":  "
     (->> (mapv l/list-sorted-by (into com/listing-cases-absolute
                                       com/listing-cases-per-100k))
          (map com/encode-cmd)
          (s/join spacer)))))

(defn reply-markup-btns [prm]
  {:reply_markup
   (json/write-str
    {:inline_keyboard
     [(reduce
       into
       (mapv (fn [type]
               (mapv (fn [case-kw]
                       {:text (str (get l/buttons case-kw)
                                   (type l/plot-type))
                        :callback_data (pr-str (assoc prm
                                                      :case case-kw
                                                      :type type))})
                     com/absolute-cases))
             [:sum :abs]))]})})

(defn worldwide? [ccode]
  (in? [ccc/worldwide-2-country-code ccc/worldwide-3-country-code
        ccc/worldwide] ccode))

(defn worldwide-plots
  ([prm] (worldwide-plots "worldwide-plots" prm))
  ([msg-id {:keys [data]}]
   (let [{ccode :cc
          chat-id :chat-id
          type :type
          case-kw :case} (edn/read-string data)]
     (let [options (reply-markup-btns {:chat-id chat-id :cc ccode})
           content (let [plot-fn (if (= type :sum)
                                   p/plot-sum-by-case p/plot-absolute-by-case)]
                     ;; the plot is fetched from the cache, stats and day need not to be
                     ;; specified
                     (plot-fn case-kw))]
       (doall
        (morse/send-photo com/telegram-token chat-id options content))
       (debugf "[%s] send-photo: %s bytes sent" msg-id (count content))))))

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
    (condp = parse_mode
      "HTML" "<b>%s</b>"
      ;; i.e. "Markdown"
      "*%s*")
    " %s")
   (com/fmt-date (:t (data/last-day pred-hm)))
   (condp = parse_mode
     "HTML" com/bot-name
     ;; i.e. "Markdown"
     (s/replace com/bot-name #"_" "\\\\_"))))

;; https://clojurians.zulipchat.com/#narrow/stream/180378-slack-archive/topic/beginners/near/191238200

(defn list-countries
  "Listing commands in the message footer correspond to the columns in the listing.
  See also `footer`, `bot-father-edit-cmds`."
  ([prm] (list-countries "list-countries" prm))
  ([_ {:keys [data msg-idx cnt-msgs sort-by-case parse_mode]}]
   (let [
         ;; TODO calculate count of reports only once
         cnt-reports (count (data/dates))
         spacer " "
         sort-indicator "▴" ;; " " "▲"
         omag-active    7 ;; order of magnitude i.e. number of digits
         omag-recov  (inc omag-active)
         omag-deaths (dec omag-active)]
     #_(debugf "[%s] cnt-reports %s" msg-id cnt-reports)
     #_(debugf "[%s] (count data) %s" msg-id (count data))
     #_(debugf "[%s] data %s" msg-id data)
     #_(debugf "[%s] pred-hm %s" msg-id pred-hm)
     (format
      (format-linewise
       [
        ["%s\n"   [(header parse_mode
                           (create-pred-hm (ccr/get-country-code ccc/worldwide)))]]
        ["%s\n"   [(format "%s %s;  %s/%s" l/day cnt-reports msg-idx cnt-msgs)]]
        ["    %s "[(str l/active    (if (= :a sort-by-case) sort-indicator " "))]]
        ["%s"     [spacer]]
        ["%s "    [(str l/recovered (if (= :r sort-by-case) sort-indicator " "))]]
        ["%s"     [spacer]]
        ["%s\n"   [(str l/deaths    (if (= :d sort-by-case) sort-indicator " "))]]
        ["%s"     [(str
                    "%s"   ; listing table
                    "%s"   ; sorted-by description; has its own new-line
                    "\n\n"
                    "%s"   ; footer
                    )]]])
      (s/join
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
                        (s/lower-case (com/encode-cmd ccode)))))
            (->> data
                 #_(take-last 11)
                 #_(partition-all 2)
                 #_(map (fn [part] (s/join "       " part))))))
      ""
      #_(if (= msg-idx cnt-msgs)
          (str "\n\n" (l/list-sorted-by-desc sort-by-case))
          "")
      (footer parse_mode)))))

(defn list-per-100k
  "Listing commands in the message footer correspond to the columns in the listing.
  See also `footer`, `bot-father-edit-cmds`."
  ([prm] (partial list-per-100k "list-per-100k" prm))
  ([_ {:keys [data msg-idx cnt-msgs sort-by-case parse_mode]}]
   (let [spacer " "
         sort-indicator "▴" ;; " " "▲"
         ;; omag - order of magnitude i.e. number of digits
         omag-active-per-100k    4
         omag-recovered-per-100k omag-active-per-100k
         omag-deaths-per-100k    (dec omag-active-per-100k)
         ]
     (format
      (format-linewise
       [["%s\n" [(header parse_mode
                         (create-pred-hm (ccr/get-country-code ccc/worldwide)))]]
        ["%s\n" [(format "%s %s;  %s/%s" l/day (count (data/dates)) msg-idx cnt-msgs)]]
        ["%s "  [(str l/active-per-1e5    (if (= :a100k sort-by-case) sort-indicator " "))]]
        ["%s"   [spacer]]
        ["%s "  [(str l/recovered-per-1e5 (if (= :r100k sort-by-case) sort-indicator " "))]]
        ["%s"   [spacer]]
        ["%s"   [(str l/deaths-per-1e5    (if (= :d100k sort-by-case) sort-indicator " "))]]
        ["\n%s" [(str
                  "%s"     ; listing table
                  "%s"     ; sorted-by description; has its own new-line
                  "\n\n%s" ; footer
                  )]]])
      (s/join
       "\n"
       (map (fn [{:keys [a100k r100k d100k cc]}]
              (let [ccode cc
                    cname (ccr/country-name-aliased ccode)]
                (format "<code>   %s%s   %s%s    %s %s</code>  %s"
                        (com/left-pad a100k " " omag-active-per-100k)
                        spacer
                        (com/left-pad r100k " " omag-recovered-per-100k)
                        spacer
                        (com/left-pad d100k " " omag-deaths-per-100k)
                        (com/right-pad cname 17)
                        (s/lower-case (com/encode-cmd ccode)))))
            (->> data
                 #_(take-last 11)
                 #_(partition-all 2)
                 #_(map (fn [part] (s/join "       " part))))))
      ""
      #_(if (= msg-idx cnt-msgs)
          (str "\n\n" (l/list-sorted-by-desc sort-by-case))
          "")
      (footer parse_mode)))))

(defn diff-coll-vals
  "Differences between values. E.g.:
  (diff-coll-valls [1 3 6 10 9 9 10])
  ;; => [2 3 4 -1 0 1]"
  [coll]
  (loop [[head & tail] coll
         result []]
    (if (and head (seq tail))
      (recur tail (conj result (- (first tail) head)))
      result)))

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

;; By default Vars are static, but Vars can be marked as dynamic to
;; allow per-thread bindings via the macro binding. Within each thread
;; they obey a stack discipline:
#_(def ^:dynamic points [[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]])
(defn create-detailed-info
  "Shows the table with the absolute and %-wise nr of cases, cases per-100k etc.
  TODO 3. show Case / Infection Fatality Rate (CFR / IFR)
  TODO Bayes' Theorem applied to PCR test: https://youtu.be/M8xlOm2wPAA
  (need 1. PCR-test accuracy, 2. Covid 19 disease prevalence)
  TODO make an api service for the content shown in the message
  TODO Create API web service(s) for every field displayed in the messages
  "
  [msg-id ccode parse_mode pred-hm]
  ;; (debugf "[%s] ccode %s" msg-id ccode)
  ;; (debugf "[%s] parse_mode %s" msg-id parse_mode)
  ;; (debugf "[%s] pred-hm %s" msg-id pred-hm)
  (format-linewise
   [["%s\n"  ; extended header
     [(format-linewise
       [["%s  " [(header parse_mode pred-hm)]]
        ["%s "  [(ccr/country-name-aliased ccode)]]
        ["%s"   [;; country commands
                 (apply (fn [ccode c3code] (format "     %s    %s" ccode c3code))
                        (map (fn [s] (com/encode-cmd (s/lower-case s)))
                             [ccode
                              (ccc/country-code-3-letter ccode)]))]]])]]
    ["%s\n" [(str l/day " " (count (data/dates)))]]
    ["%s\n" ; data
     [(let [
            data-active (:a (data/case-counts-report-by-report pred-hm))
            ]
        ;; (debugf "[%s] max-active-date %s" msg-id max-active-date)
        ;; (debugf "[%s] last-day %s" msg-id last-day)
        ;; (debugf "[%s] confirmed %s" msg-id confirmed)
        ;; (debugf "[%s] population-rounded %s" msg-id population-rounded)
        ;; (debugf "[%s] (count data-active) %s" msg-id (count data-active))
        ;; (debugf "[%s] (type data-active) %s" msg-id (type data-active))
        (let [
              max-active-val (apply max data-active)
              max-active-idx (last-index-of data-active max-active-val)
              max-active-date (nth (data/dates) max-active-idx)
              last-day (data/last-day pred-hm)
              {confirmed :c population :p} last-day
              population-rounded (utn/round-div-precision population 1e6 1)
              delta (data/delta pred-hm)
              {delta-confirmed :c} delta
              ]
          ;; (debugf "[%s] delta %s" msg-id delta)
          ;; (debugf "[%s] max-active-val %s" msg-id max-active-val)
          ;; (debugf "[%s] max-active-idx %s" msg-id max-active-idx)
          ;; (debugf "[%s] delta-confirmed %s" msg-id delta-confirmed)
          (format-linewise
           (apply
            conj
            [["%s\n" [(format "<code>%s %s</code> = %s %s"
                              (com/right-pad l/people " " (- padding-s 2))
                              (com/left-pad population " " (+ padding-n 2))
                              population-rounded
                              l/millions-rounded)]]
             ["%s\n" [(fmt-to-cols {:s l/confirmed :n confirmed
                                    :diff delta-confirmed})]]]
            (do
              #_(debug "[%s] (pos? confirmed)" msg-id (pos? confirmed))
              (when (pos? confirmed)
                (let [{deaths             :d
                       recovered          :r
                       active             :a
                       active-per-100k    :a100k
                       recovered-per-100k :r100k
                       deaths-per-100k    :d100k
                       closed-per-100k    :c100k
                       a-rate :a-rate
                       r-rate :r-rate
                       d-rate :d-rate
                       c-rate :c-rate ;; closed-rate
                       } last-day
                      {active-last-8-reports :a} (data/last-8-reports pred-hm)
                      [active-last-8th-report & active-last-7-reports] active-last-8-reports
                      closed (+ deaths recovered)
                      {delta-deaths :d delta-recov :r delta-active :a
                       delta-d100k :d100k delta-r100k :r100k delta-a100k :a100k
                       } delta
                      delta-closed (+ delta-deaths delta-recov)]
                  #_(debugf "[%s] delta-closed" msg-id delta-closed)
                  #_(debugf "[%s] delta-confirmed" msg-id delta-confirmed)
                  #_(debugf "[%s] (= delta-confirmed delta-closed)"
                            msg-id (= delta-confirmed delta-closed))
                  [
                   ["%s\n" [(fmt-to-cols
                             {:s l/active :n active :diff delta-active
                              :rate a-rate})]]
                   ["%s\n" [(fmt-to-cols
                             {:s l/active-per-1e5 :n active-per-100k
                              :diff delta-a100k
                              })]]
                   ["%s\n" [(format "<code>%s %s</code> %s"
                                    (com/right-pad l/active-max " " padding-s)
                                    (com/left-pad max-active-val " " padding-n)
                                    (format "(%s)"
                                            (com/fmt-date max-active-date)))]]

                   ;; TODO add effective reproduction number (R)
                   #_["%s\n" [(fmt-to-cols
                             {:s l/active-last-7-med
                              :n (->> active-last-7-reports (izoo/roll-median 7) (first)
                                      (int))
                              :diff ""
                              :calc-diff false
                              })]]
                   ["%s\n" [(fmt-to-cols
                             {:s l/active-last-7-avg
                              :n (-> active-last-7-reports istats/mean round-nr)
                              :diff ""
                              :calc-diff false})]]
                   ["%s\n" [(fmt-to-cols
                             {:s l/active-change-last-7-avg
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
                              :n (-> (/ (- active active-last-8th-report) 7.0)
                                     round-nr plus-minus)
                              :diff ""
                              :calc-diff false})]]
                   ["%s\n" [(fmt-to-cols
                             {:s l/recovered :n recovered
                              :diff delta-recov
                              :rate r-rate})]]
                   ["%s\n" [(fmt-to-cols
                             {:s l/recovered-per-1e5 :n recovered-per-100k
                              :diff delta-r100k
                              })]]
                   ["%s\n" [(fmt-to-cols
                             {:s l/deaths :n deaths :diff delta-deaths
                              :rate d-rate})]]
                   ["%s\n" [(fmt-to-cols
                             {:s l/deaths-per-1e5 :n deaths-per-100k
                              :diff delta-d100k
                              })]]
                   ["%s\n" [(fmt-to-cols
                             {:s l/closed :n closed :diff delta-closed
                              :rate c-rate})]]
                   ["%s\n\n" [(fmt-to-cols
                               {:s l/closed-per-1e5 :n closed-per-100k
                                :diff delta-d100k
                                ;; TODO create command l/cmd-closed-per-1e5
                                #_#_:desc (com/encode-cmd l/cmd-closed-per-1e5)})]]
                   ["%s\n" [(format
                             #_"%s\n%s"
                             "<code>%s</code>\n%s"
                             #_"<code>%s\n%s</code>" l/active-last-7
                             (u/sjoin active-last-7-reports))]]

                   ;; no country ranking can be displayed for worldwide statistics
                   (do
                     #_(debug "[%s] worldwide?" msg-id (worldwide? ccode))
                     (let [rank (first
                                 (map :rank
                                      (filter (fn [{:keys [ccode]}] (= ccode ccode))
                                              (data/all-rankings))))
                           cnt-countries (count ccc/all-country-codes)
                           worldwide-block
                           (if (worldwide? ccode)
                             ["" [""]]
                             ["\n%s"
                              [(format-linewise
                                [["%s" [l/people            :p]]
                                 ["%s" [l/active-per-1e5    :a100k]]
                                 ["%s" [l/recovered-per-1e5 :r100k]]
                                 ["%s" [l/deaths-per-1e5    :d100k]]
                                 ["%s" [l/closed-per-1e5    :c100k]]]
                                :line-fmt (str "<code>%s</code>: %s / " cnt-countries "\n")
                                :fn-fmts
                                (fn [fmts] (format l/randking-desc
                                                  cnt-countries (s/join "" fmts)))
                                :fn-args
                                (fn [args] (update args (dec (count args))
                                                  (fn [_]
                                                    (get rank (last args))))))]])]
                       ;; (debugf "[%s] (count worldwide-block) %s"
                       ;;         msg-id (count worldwide-block))
                       worldwide-block))])))))))]]
    ["%s\n" [(footer parse_mode)]]]))

(defn detailed-info
  "Doesn't need to specify the rest of parameters. The retval will be fetched from
  the cache."
  ([ccode]
   (detailed-info "detailed-info" ccode "HTML" (create-pred-hm ccode)))
  ([msg-id ccode parse_mode pred-hm]
   ;; (debugf "[%s] ccode %s; parse_mode %s; pred-hm %s"
   ;;         msg-id ccode parse_mode pred-hm)
   (let [content (data/from-cache
                  (fn [] (create-detailed-info msg-id ccode parse_mode pred-hm))
                  [:msg (keyword ccode)])]
     (debugf "[%s] ccode %s msg-size %s" msg-id ccode (count content))
     content)))

(defn feedback []
  (str "Just write a message to @RostislavSvoboda thanks."))

(defn contributors [parse_mode]
  (format "%s\n\n%s\n\n%s"
          (s/join "\n" ["@DerAnweiser"
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
   " " com/commit " "
   (str
    (link "GitHub" "https://github.com/Bost/corona_cases" parse_mode) ", "
    (link "GitLab" "https://gitlab.com/rostislav.svoboda/corona_cases" parse_mode)
    "\n")
   "\n"
   (format "- %s cases = %s + %s\n" l/closed l/recovered l/deaths)
   (format "- Percentage calculation: <cases> / %s\n" l/confirmed)
   (format (str "- %s:\n"
                "  %s\n")
           l/active-max
           (:doc (meta #'l/active-max)))
   (format (str "- %s:\n"
                "  %s\n")
           l/active-last-7
           (:doc (meta #'l/active-last-7)))
   (format (str "- %s:\n"
                "  %s\n")
           l/active-last-7-med
           (:doc (meta #'l/active-last-7-med)))
   (format (str "- %s:\n"
                "  %s\n")
           l/active-last-7-avg
           (:doc (meta #'l/active-last-7-avg)))
   (format (str "- %s = (%s - %s) / 7\n"
                "  %s\n")
           l/active-change-last-7-avg
           l/active l/active-last-8th
           (:doc (meta #'l/active-change-last-7-avg)))
   #_(str
      "\n"
      " - " (link "Home page"
                  (def home-page
                    (cond
                      com/env-prod? "https://corona-cases-bot.herokuapp.com/"
                      com/env-test? "https://hokuspokus-bot.herokuapp.com/"
                      :else "http://localhost:5050"))
                  parse_mode))
   ;; (abbreviated) content of the former reference message
   (format (str "- %s, %s, %s, %s:\n"
                "  %s\n")
           l/active-per-1e5
           l/recovered-per-1e5
           l/deaths-per-1e5
           l/closed-per-1e5
           "Cases per 100 000 people")
   "\n"
   (format "%s %s\n"
           "- Robert Koch-Institut "
           (link "COVID-19 (Coronavirus SARS-CoV-2)"
                 ref-rober-koch parse_mode))
   (format "- 3Blue1Brown: %s\n"
           (link "Exponential growth and epidemics"
                 ref-3blue1brown-exp-growth
                 parse_mode))
   (format "%s\n  %s\n  %s\n"
           "- Worldometer - COVID-19 Coronavirus"
           (link "Coronavirus Age Sex Demographics" ref-age-sex parse_mode)
           (link "Mortality rate" ref-mortality-rate parse_mode))
   (format "- Thanks goes to %s. Please send %s \n"
           (com/encode-cmd l/contributors)
           (com/encode-cmd l/feedback))
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
