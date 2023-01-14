(ns town.lilac.dom
  (:require
   ["incremental-dom" :as dom]
   [goog.object :as gobj])
  (:require-macros
   [town.lilac.dom :refer [$]]))


(defn open
  "Open an element for a specific `tag`.
  Not meant for use directly. See `$` and other DOM macros."
  [tag key attrs]
  (dom/elementOpenStart tag key nil)
  (doseq [[k v] attrs]
    (dom/attr (name k) (clj->js v)))
  (dom/elementOpenEnd))


(defn close
  "Close an element for a specific `tag`.
  Not meant for use directly. See `$` and other DOM macros."
  [tag]
  (dom/elementClose tag))


(defn void
  "Create an element out of a tag that does not close, e.g. \"input\".
  Not meant for use directly. See `$` and other DOM macros."
  [tag key attrs]
  (open tag key attrs)
  (close tag))


(defn text
  "Create a DOM text node."
  [& args]
  (dom/text (apply str args)))


;; https://github.com/google/incremental-dom/issues/283
(defn html-blob
  "Create an HTML blob out of string `content`, rendering it directly on the
  page when patched."
  [content]
  (let [el (dom/elementOpen "html-blob")]
    (when (not= content (gobj/get el "__innerHTML"))
      (gobj/set el "__innerHTML" content)
      (gobj/set el "innerHTML" content))
    (dom/skip)
    (dom/elementClose "html-blob")))


(defn patch
  "Given a root DOM element `root` and a function `f` that contains DOM
  expressions, calls `f` with no arguments and updates the inner HTML of the
  root element with the result."
  [root f]
  (dom/patch root f))


(comment
  (def *state (atom {:text "bonjour"}))

  (defn on-change
    [text]
    (swap! *state assoc :text text))

  (defn example
    [state on-change]
    ($ "div"
       {:style {:fontFamily "sans-serif"}}
       ($ "input"
          {:style {:border "1px solid red"}
           :oninput (fn [e]
                      (on-change (.. e -target -value)))
           :value (:text @*state)
           :& (when (= "hello" (:text state))
                {:style {:border "1px solid blue"}})})
       ($ "div" (text (:text state)))))

  (defn render!
    [state]
    (dom/patch (js/document.getElementById "root")
               #(example state on-change)))

  (add-watch *state :render (fn [_ _ _ state] (render! state)))

  (render! @*state)

  (swap! *state assoc :text "hi")

 )
