(printf "Current-ns [%s] loading %s\n" *ns* 'corona.messages)

(ns corona.messages
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.string :as s]
   [corona.api.expdev07 :as data]
   [corona.api.v1 :as v1]
   [corona.common :as com]
   [corona.countries :as ccr]
   [corona.country-codes :as ccc :refer :all]
   [corona.lang :as l]
   [corona.plot :as p]
   [morse.api :as morse]
   [utils.core :as u :refer [in? dbg dbgv dbgi] :exclude [id]]
   [utils.num :as utn]
   [incanter.stats :as istats]
   [incanter.zoo :as izoo]
   [taoensso.timbre :as timbre :refer :all]

   [corona.common :as co])
  (:import java.awt.image.BufferedImage
           java.io.ByteArrayOutputStream
           javax.imageio.ImageIO))

;; (debugf "Loading namespace %s" *ns*)

(defn bot-name-formatted []
  (s/replace com/bot-name #"_" "\\\\_"))

(def ^:const options {:parse_mode "Markdown" :disable_web_page_preview true})

(defn create-pred-hm [country-code] (data/create-pred-hm country-code))

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
                 fn-args identity} :as prm}]
  {:pre [(let [cnt-fmt-specifiers (count (re-seq #"%s" line-fmt))]
           (and (pos? cnt-fmt-specifiers)
                (apply = cnt-fmt-specifiers
                       (map (fn [line] (count (second line))) lines))))]}
  (apply format
         (->> lines (map first) (fn-fmts) (reduce str))
         (map (fn [line] (apply format line-fmt
                               (fn-args (second line))))
              lines)))

(defn fmt-to-cols-narrower
  "Info-message numbers of aligned to columns for better readability"
  [{:keys [s n total diff desc calc-rate show-n calc-diff]
    :or {show-n true calc-diff true
         desc ""}}]
  (format "<code>%s %s</code> %s"
          (com/right-pad s " " (- padding-s 2))
          (com/left-pad (if show-n n "") " " (+ padding-n 2))
          desc))

(defn fmt-val-to-cols
  [{:keys [s n show-n desc]
    :or {show-n true desc ""}}]
  (format "<code>%s %s</code> %s"
          (com/right-pad s " " padding-s)
          (com/left-pad (if show-n n "") " " padding-n)
          desc))

(defn fmt-to-cols
  "Info-message numbers of aligned to columns for better readability"
  [{:keys [s n total diff calc-rate show-n calc-diff
           s1 n1 cmd1]
    :or {show-n true calc-diff true
         s1 "" n1 "" cmd1 ""}}]
  (format "<code>%s %s %s %s  %s %s </code>%s"
          (com/right-pad s " " padding-s)
          (com/left-pad (if show-n n "") " " padding-n)
          (com/left-pad (if calc-rate (str (utn/percentage n total) "%") " ")
                      " " 4)
          (if calc-diff
            (plus-minus diff)
            (com/left-pad "" " " max-diff-order-of-magnitude))
          s1
          (com/left-pad n1 " " 4)
          (com/encode-cmd cmd1)))

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
  (let [spacer "   "]
    (str
     ;; "Try" spacer
     (->> [l/world l/explain]
          (map com/encode-cmd)
          (map (fn [cmd] (com/encode-pseudo-cmd cmd parse_mode)))
          (s/join spacer))
     spacer l/listings ":  "
     (->> (mapv l/list-sorted-by com/listing-cases-absolute)
          (map com/encode-cmd)
          (s/join spacer)))))

(defn toByteArrayAutoClosable
  "Thanks to https://stackoverflow.com/a/15414490"
  [^BufferedImage image]
  (with-open [out (new ByteArrayOutputStream)]
    (ImageIO/write image "png" out)
    (let [array (.toByteArray out)]
      (debugf "image-size %s" (count array))
      array)))

(defn buttons [prm]
  {:reply_markup
   (json/write-str
    {:inline_keyboard
     [(reduce
       into
       (mapv (fn [type]
               (mapv (fn [case-kw]
                       {:text (str (case-kw l/buttons)
                                   (type l/plot-type))
                        :callback_data (pr-str (assoc prm
                                                      :case case-kw
                                                      :type type))})
                     com/absolute-cases))
             [:sum :abs]))]})})

(defn worldwide? [country-code]
  (let [r (in? [ccc/worldwide-2-country-code ccc/worldwide-3-country-code
                ccc/worldwide] country-code)]
    #_(debugf "[worldwide?] (worldwide? %s) %s" country-code r)
    r))

(defn callback-handler-fn [{:keys [data] :as prm}]
  (let [{country-code :cc
         chat-id :chat-id
         type :type
         case-kw :case} (edn/read-string data)]
    (when (worldwide? country-code)
      (doall
       (morse/send-photo
        com/telegram-token chat-id
        (buttons {:chat-id chat-id :cc country-code})
        (toByteArrayAutoClosable
         (let [plot-fn (if (= type :sum) p/plot-all-by-case p/plot-all-absolute)]
           (plot-fn
            case-kw
            #_{
             :case case-kw
             :type type
             }
            #_{
             :day (count (data/dates))
             :case case-kw
             :type type
             :threshold (com/min-threshold case-kw)
             :threshold-increase (com/threshold-increase case-kw)
             :stats (v1/pic-data)
             }
            ))))))))

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
   (com/fmt-date (:f (data/last-nn-day pred-hm)))
   (condp = parse_mode
     "HTML" com/bot-name
     ;; i.e. "Markdown"
     (s/replace com/bot-name #"_" "\\\\_"))))

;; https://clojurians.zulipchat.com/#narrow/stream/180378-slack-archive/topic/beginners/near/191238200

(defn list-countries
  "Listing commands in the message footer correspond to the columns in the listing.
  See also `footer`, `bot-father-edit-cmds`."
  [{:keys [data msg-idx cnt-msgs sort-by-case parse_mode pred]}]
  #_(debugf "[list-countries]")
  (let [
        ;; TODO calculate count of reports only once
        cnt-reports (count (data/dates))
        spacer " "
        sort-indicator "▴" ;; " " "▲"
        omag-active    7 ;; order of magnitude i.e. number of digits
        omag-recov  (inc omag-active)
        omag-deaths (dec omag-active)]
    #_(debugf "cnt-reports %s" cnt-reports)
    #_(debugf "(count data) %s" (count data))
    #_(debugf "data %s" data)
    #_(debugf "pred %s" pred)
    (format
     (format-linewise
      [
       ["%s\n"   [(header parse_mode pred)]]
       ["%s\n"   [(format "%s %s;  %s/%s" l/day cnt-reports msg-idx cnt-msgs)]]
       ["    %s "[(str l/active    (if (= :i sort-by-case) sort-indicator " "))]]
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
      (map (fn [{:keys [i r d cc] :as stats}]
             #_(debugf "stats %s" stats)
             (let [cn (ccr/country-name-aliased cc)]
               (format "<code>%s%s%s%s%s %s</code>  %s"
                       (com/left-pad i " " omag-active)
                       spacer
                       (com/left-pad r " " omag-recov)
                       spacer
                       (com/left-pad d " " omag-deaths)
                       (com/right-pad cn 17)
                       (s/lower-case (com/encode-cmd cc)))))
           (->> data
                #_(take-last 11)
                #_(partition-all 2)
                #_(map (fn [part] (s/join "       " part))))))
     ""
     #_(if (= msg-idx cnt-msgs)
         (str "\n\n" (l/list-sorted-by-desc sort-by-case))
         "")
     (footer parse_mode))))

(defn list-per-100k
  "Listing commands in the message footer correspond to the columns in the listing.
  See also `footer`, `bot-father-edit-cmds`."
  [{:keys [data msg-idx cnt-msgs sort-by-case parse_mode pred]}]
  #_(debugf "list-per-100k")
  (let [spacer " "
        sort-indicator "▴" ;; " " "▲"
        ;; omag - order of magnitude i.e. number of digits
        omag-active-per-100k    4
        omag-recovered-per-100k omag-active-per-100k
        omag-deaths-per-100k    (dec omag-active-per-100k)
        ]
    (format
     (format-linewise
      [["%s\n" [(header parse_mode pred)]]
       ["%s\n" [(format "%s %s;  %s/%s" l/day (count (data/dates)) msg-idx cnt-msgs)]]
       ["%s "  [(str l/active-per-1e5    (if (= :i100k sort-by-case) sort-indicator " "))]]
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
      (map (fn [{:keys [i100k r100k d100k cc] :as stats}]
             (let [cn (ccr/country-name-aliased cc)]
               (format "<code>   %s%s   %s%s    %s %s</code>  %s"
                       (com/left-pad i100k " " omag-active-per-100k)
                       spacer
                       (com/left-pad r100k " " omag-recovered-per-100k)
                       spacer
                       (com/left-pad d100k " " omag-deaths-per-100k)
                       (com/right-pad cn 17)
                       (s/lower-case (com/encode-cmd cc)))))
           (->> data
                #_(take-last 11)
                #_(partition-all 2)
                #_(map (fn [part] (s/join "       " part))))))
     ""
     #_(if (= msg-idx cnt-msgs)
       (str "\n\n" (l/list-sorted-by-desc sort-by-case))
       "")
     (footer parse_mode))))

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

;; By default Vars are static, but Vars can be marked as dynamic to
;; allow per-thread bindings via the macro binding. Within each thread
;; they obey a stack discipline:
#_(def ^:dynamic points [[0 0] [1 3] [2 0] [5 2] [6 1] [8 2] [11 1]])
(defn detailed-info
  "Shows the table with the absolute and %-wise nr of cases, cases per-100k etc.
  TODO 1. 'Country does not report recovered cases'
  TODO 2. Estimate recovered cased (based on 1.) with avg recovery time 14 days
  TODO 3. show Case / Infection Fatality Rate (CFR / IFR)
  TODO Bayes' Theorem applied to PCR test: https://youtu.be/M8xlOm2wPAA
  (need 1. PCR-test accuracy, 2. Covid 19 disease prevalence)
  TODO make an api service for the content shown in the message
  TODO Create API web service(s) for every field displayed in the messages
  "
  [{:keys [country-code parse_mode pred]}]
  (debugf "[detailed-info] %s" pred)
  (let [rank (first
              (map :rank
                   (filter (fn [{:keys [cc]}] (= cc country-code))
                           (data/all-rankings))))]
    (debugf "[detailed-info] rank %s" rank)
    (let [
          cnt-countries (count ccc/all-country-codes)
          content
          (format-linewise
           [["%s\n"  ; extended header
             [(format-linewise
               [["%s  " [(header parse_mode pred)]]
                ["%s "  [(ccr/country-name-aliased country-code)]]
                ["%s"   [;; country commands
                         (apply (fn [cc ccc] (format "     %s    %s" cc ccc))
                                (map (fn [s] (com/encode-cmd (s/lower-case s)))
                                     [country-code
                                      (ccc/country-code-3-letter country-code)]))]]])]]
            ["%s\n" [(str l/day " " (count (data/dates)))]]
            (do
                ["%s\n" ; data
                 [(let [
                        data-active (:i (data/case-counts-report-by-report pred))
                        ]
                    #_(debugf "data-active %s" (count data-active))
                    ;; (debugf "max-active-val %s" max-active-val)
                    ;; (debugf "max-active-idx %s" max-active-idx)
                    ;; (debugf "max-active-date %s" max-active-date)
                    ;; (debugf "last-day %s" last-day)
                    ;; (debugf "confirmed %s" confirmed)
                    ;; (debugf "population-rounded %s" population-rounded)
                    ;; (debugf "delta %s" delta)
                    (let [
                          max-active-val (apply max data-active)
                          max-active-idx (.lastIndexOf data-active max-active-val)
                          max-active-date (nth (data/dates) max-active-idx)
                          last-day (data/last-nn-day pred)
                          {confirmed :c population :p} last-day
                          population-rounded (utn/round-div-precision population 1e6 1)
                          delta (data/delta pred)
                          {delta-confirmed :c} delta
                          ]
                      #_(debugf "delta-confirmed %s" delta-confirmed)
                      (format-linewise
                       (apply
                        conj
                        [["%s\n" [(fmt-to-cols-narrower
                                   {:s l/people :n population :calc-rate false
                                    :calc-diff false
                                    :desc (format "= %s %s" population-rounded
                                                  l/millions-rounded)})]]
                         ["%s\n" [(fmt-to-cols {:s l/confirmed :n confirmed
                                                :diff delta-confirmed :calc-rate false})]]]
                        (do
                          #_(debug "[detailed-info] (pos? confirmed)" (pos? confirmed))
                          (when (pos? confirmed)
                            (let [{deaths             :d
                                   recovered          :r
                                   active             :i
                                   active-per-100k    :i100k
                                   recovered-per-100k :r100k
                                   deaths-per-100k    :d100k
                                   closed-per-100k    :c100k
                                   } last-day
                                  {last-8-reports :i} (data/last-nn-8-reports pred)
                                  [last-8th-report & last-7-reports] last-8-reports
                                  [last-7th-report & _] last-7-reports
                                  closed (+ deaths recovered)
                                  {delta-deaths :d delta-recov :r delta-active :i} delta
                                  delta-closed (+ delta-deaths delta-recov)]
                              #_(debug "[detailed-info]"
                                       "delta-closed" delta-closed
                                       "delta-confirmed" delta-confirmed
                                       "(= delta-confirmed delta-closed)" (= delta-confirmed delta-closed))
                              [["%s\n" [(fmt-to-cols
                                         {:s l/active :n active :total confirmed :diff delta-active
                                          :calc-rate true
                                          :s1 l/active-per-1e5 :n1 active-per-100k
                                          :cmd1 l/cmd-active-per-1e5})]]
                               ["%s\n" [(fmt-val-to-cols
                                         {:s l/active-max :n max-active-val :show-n true
                                          :desc (format "(%s)"
                                                        (com/fmt-date max-active-date))})]]

                               ;; TODO add effective reproduction number (R)
                               ["%s\n" [(fmt-to-cols
                                         {:s l/active-last-7-med
                                          :n (->> last-7-reports (izoo/roll-median 7) (first)
                                                  (int))
                                          :total population :diff "" :calc-rate false
                                          :show-n true :calc-diff false})]]
                               ["%s\n" [(fmt-to-cols
                                         {:s l/active-last-7-avg
                                          :n (-> last-7-reports istats/mean round-nr)
                                          :total population :diff "" :calc-rate false
                                          :show-n true :calc-diff false})]]
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
                                          :n (-> (/ (- active last-8th-report) 7.0)
                                                 round-nr plus-minus)
                                          :total population :diff "" :calc-rate false
                                          :show-n true :calc-diff false})]]
                               ["%s\n" [(fmt-to-cols
                                         {:s l/recovered :n recovered :total confirmed
                                          :diff delta-recov :calc-rate true :s1 l/recovered-per-1e5
                                          :n1 recovered-per-100k
                                          :cmd1 l/cmd-recovered-per-1e5})]]
                               ["%s\n" [(fmt-to-cols
                                         {:s l/deaths :n deaths :total confirmed :diff delta-deaths
                                          :calc-rate true
                                          :s1 l/deaths-per-1e5 :n1 deaths-per-100k
                                          :cmd1 l/cmd-deaths-per-1e5})]]
                               ["%s\n\n" [(fmt-to-cols
                                           {:s l/closed :n closed :total confirmed
                                            :diff delta-closed :calc-rate true
                                            :s1 l/closed-per-1e5
                                            ;; TODO :cmd1 l/cmd-closed-per-1e5
                                            :n1 closed-per-100k})]]
                               ["%s\n" [(format
                                         #_"%s\n%s"
                                         "<code>%s</code>\n%s"
                                         #_"<code>%s\n%s</code>" l/active-last-7
                                         (u/sjoin last-7-reports))]]

                               ;; no country ranking can be displayed for worldwide statistics
                               (do
                                 #_(debug "[detailed-info] worldwide?" (worldwide? country-code))
                                 (let [worldwide-block
                                       (if (worldwide? country-code)
                                         ["" [""]]
                                         ["\n%s"
                                          [(format-linewise
                                            [["%s" [l/people            :p]]
                                             ["%s" [l/active-per-1e5    :i100k]]
                                             ["%s" [l/recovered-per-1e5 :r100k]]
                                             ["%s" [l/deaths-per-1e5    :d100k]]
                                             ["%s" [l/closed-per-1e5    :c100k]]]
                                            :line-fmt (str "<code>%s</code>: %s / " cnt-countries "\n")
                                            :fn-fmts
                                            (fn [fmts] (format "Ranking on the list of all %s countries:\n%s"
                                                              cnt-countries
                                                              (s/join "" fmts)))
                                            :fn-args
                                            (fn [args] (update args (dec (count args))
                                                              (fn [_]
                                                                (get rank (last args))))))]])]
                                   #_(debug "[detailed-info] (count worldwide-block)" (count worldwide-block))
                                   worldwide-block))])))))))]])
            ["%s\n" [(footer parse_mode)]]])]
      (debugf "[detailed-info] country-code %s; message-size %s chars"
              country-code (count content))
      content)))

(defn feedback [parse_mode]
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
