(ns corona.core
  (:require
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rd]
   ;; this namespace initializes Klipse. We require it for its side effects
   [klipse.run.plugin.plugin]
   [klipse.plugin :as klipse-plugin]
   ))

(enable-console-print!)

(println
 (str
  "This text is printed from src/cljs/corona/core.cljs. "
  "Go ahead and edit it and see reloading in action."))

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Hello world!"}))

(defn klipse-wrapper
  [{:keys [scripts content settings]}]
  (reagent/create-class
   {:component-did-mount (fn [_]
                           (klipse-plugin/init (clj->js settings)))
    :reagent-render      (fn [{:keys [content]}]
                           content)}))

(defn klipse-snippet []
  [klipse-wrapper
   {:content
    [:div
     [:div.klipse.language-klipse
      "(require '[reagent.core :as r])"]
     [:div.klipse.language-reagent
      "[:div {:style {:color \"red\"}} \"hello world!\"]"]
     [:div.klipse.language-klipse "(+ 3 3)"]]
    :settings {:selector ".language-klipse"
               :selector_reagent ".language-reagent"}}])

#_(defn mount-root []
  (reagent/render [:div [klipse-snippet]] (.getElementById js/document "app")))

#_(mount-root)

(defn hello-world []
  [:div
   [klipse-snippet]
   [:h1 (:text @app-state)]
   [:h3 "Edit this and watch it change!"]])

(rd/render [hello-world]
           (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
