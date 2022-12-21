(ns town.lilac.dom
  (:require
   ["incremental-dom" :as dom]
   [goog.object :as gobj])
  (:require-macros
   [town.lilac.dom :refer [$]]))


(defn open
  [tag key attrs]
  (apply
   dom/elementOpen tag key nil
   (when attrs
     (apply concat (js/Object.entries (clj->js attrs))))))


(defn close
  [tag]
  (dom/elementClose tag))


(defn void
  [tag key attrs]
  (apply
   dom/elementVoid tag key nil
   (when attrs
     (apply concat (js/Object.entries (clj->js attrs))))))


(defn text [& args] (dom/text (apply str args)))

;; https://github.com/google/incremental-dom/issues/283
(defn raw-html
  [content]
  (let [el (dom/elementOpen "html-blob")]
    (when (not= content (gobj/get el "__innerHTML"))
      (gobj/set el "__innerHTML" content)
      (gobj/set el "innerHTML" content))
    (dom/skip)
    (dom/elementClose "html-blob")))

(def patch dom/patch)

(comment
  (def *state (atom {:text "bonjour"}))

  (defn example
   []
   ($ "div"
      {:style {:fontFamily "sans-serif"}}
      ($ "input"
         {:style {:border "1px solid red"}
          :oninput (fn [e]
                     (swap! *state assoc :text (.. e -target -value)))
          :value (:text @*state)})
      ($ "div" (text (:text @*state)))))

 (defn render!
   []
   (dom/patch (js/document.getElementById "root") example))

 (add-watch *state :render (fn [_ _ _ _] (render!)))

 (render!)

 (swap! *state assoc :text "hi")

 )
