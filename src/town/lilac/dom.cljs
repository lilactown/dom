(ns town.lilac.dom
  "An extremely simple library for declaratively creating and updating web pages
  in ClojureScript.

  It is a thin wrapper around google's extremely stable incremental-dom library.

  The library exposes a `$` macro that allows one to create elements that are
  then diffed against the existing nodes on the page and patched accordingly. No
  virtual DOM is kept in memory.

  There are helpful macros that wrap `$` for specific elements like `div`,
  `button`, `input`, etc. With these, the library provides a simple DSL for
  constructing elements.

  These macro calls must be run via a function passed to `patch`.

  ## How it works

  incremental-dom operates via side- effects; when you call $ or a specific DOM
  macro like div via this library, the library does some internal book keeping
  to track what elements contain others via the order of open and close calls.

  After the function you passed to patch returns, it will take the tree of
  elements constructed, diff the resulting tree with what is present within the
  root element, and update the root with nodes that have changed.

  What this means: you do not need to return an element for it to be added to
  the result.

  ## Dynamic attributes

  The $ macro needs to determine whether an argument passed to it is a child in
  order to place the open and close calls correctly. The heuristic it uses is:
  anything that isn't a map literal in the first position is a child. This
  complicates things when you want to pass in a dynamic map of attributes.

  Instead, the $ and other DOM element macros accept a special attribute, & or
  :& which will merge any static attributes you pass in with ones that are
  passed in dynamically."
  (:refer-clojure :exclude [use flush])
  (:require
   ["incremental-dom" :as dom]
   [goog.object :as gobj])
  (:require-macros
   [town.lilac.dom :as macro :refer [$]]))

(def ^:dynamic *buffer* nil)


(defn- open*
  [tag key attrs]
  (dom/elementOpenStart tag key nil)
  (doseq [[k v] attrs]
    (dom/attr (name k) (clj->js v)))
  (dom/elementOpenEnd))


(defn open
  "Open an element for a specific `tag`.
  Not meant for use directly. See `$` and other DOM macros."
  [tag key attrs]
  (if (some? *buffer*)
    (.push *buffer* #js ["open" tag key attrs])
    (open* tag key attrs)))


(defn close
  "Close an element for a specific `tag`.
  Not meant for use directly. See `$` and other DOM macros."
  [tag]
  (if *buffer*
    (.push *buffer* #js ["close" tag])
    (dom/elementClose tag)))


(defn void
  "Create an element out of a tag that does not close, e.g. \"input\".
  Not meant for use directly. See `$` and other DOM macros."
  [tag key attrs]
  (open tag key attrs)
  (close tag))


(defn text
  "Create a DOM text node."
  [& args]
  (if *buffer*
    (.push *buffer* #js ["text" args])
    (dom/text (apply str args))))


(defn flush!
  []
  (doseq [data *buffer*]
    (case (first data)
      "open" (apply open* (.slice data 1))
      "close" (dom/elementClose (second data))
      "text" (dom/text (apply str (second data))))))


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

;; https://github.com/google/incremental-dom/issues/314
(defn html-comment
  [text]
  (prn text)
  (let [cur-el (dom/currentElement)
        ptr (dom/currentPointer)
        el (if (and (some? ptr) (= "#comment" (.-nodeName ptr)))
             ptr
             (as-> (js/document.createComment "") el
               (.insertBefore cur-el el ptr)))]
    (set! (.-data el) text)
    (dom/skipNode)
    el))


(defn patch
  "Given a root DOM element `root` and a function `f` that contains DOM
  expressions, calls `f` with no arguments and updates the inner HTML of the
  root element with the result."
  [root f]
  (dom/patch root f))

(def get-current-element dom/currentElement)

(defn patch-outer
  "Like `patch`, but replaces the root element completely with the result of
  `f`."
  [root f]
  (dom/patchOuter root f))


(defn patch-range
  "Like `patch-outer`, but takes a beginning element `el`, end element `el2` and
  replaces them in the dom with the result of `f`."
  [^js el1 ^js el2 f]
  (let [parent (.-parentNode el1)]
    (loop [el el1]
      (if (some? el)
        (let [sibling (.-nextSibling el)]
          (.removeChild parent el)
          (if (identical? el2 sibling)
            (patch-outer sibling f)
            (recur sibling)))
        (throw (ex-info "Reached last sibling and never found el2"
                        {:el1 el1 :el2 el2}))))))


(defn use
  "If `v` is a Promise, throws an exception that should be caught by a wrapping
  `async` call somewhere in the call stack above it. Otherwise, returns `v`."
  [v]
  (if (= js/Promise (type v))
    (throw v)
    v))


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
           & (when (= "hello" (:text state))
                {:style {:border "1px solid blue"}})})
       ($ "div" (text (:text state)))))

  (defn render!
    [state]
    (dom/patch (js/document.getElementById "root")
               #(example state on-change)))

  (add-watch *state :render (fn [_ _ _ state] (render! state)))

  (render! @*state)

  (swap! *state assoc :text "hi")

  (patch
   (js/document.getElementById "root")
   (fn []
     ($ "div" (text "hi"))
     (macro/try
      ($ "div" {:style {:border "1px solid blue"}}
         (throw (ex-info "error" {}))
         ($ "textarea" (text "hi")))
      (catch js/Object _
        ($ "div" (text "Error!"))))))


  (def cache nil)

  (defn fetcher
    []
    (if (nil? cache)
      (-> (js/Promise. (fn [res]
                         (js/setTimeout #(res {:foo "bar"})
                                        2000)))
          (.then (fn [v] (set! cache v))))
      cache))

  (patch
   (js/document.getElementById "root")
   (fn []
     (set! cache nil)
     ($ "div" (text "hi"))
     (macro/async
      ($ "div" {:style {:border "1px solid blue"}}
         ($ "textarea" (text (pr-str (use (fetcher))))))
      (fallback
       (text "foo")
       ($ "div" (text "loading..."))))))

 )
