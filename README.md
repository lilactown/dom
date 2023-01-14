# town.lilac/dom

An extremely simple library for declaratively creating and updating web pages in
ClojureScript.

It is a thin wrapper around google's extremely stable
[incremental-dom](https://github.com/google/incremental-dom) library.

## Install

The library is available via git deps

```clojure
town.lilac/dom {:git/url "https://github.com/lilactown/dom"
                :git/sha "172f364f572dcfd0e8849738a52e8cad95e3e75b"}
```

## Usage

The library exposes a `$` macro that allows one to create elements that are then
diffed against the existing nodes on the page and patched accordingly.
No virtual DOM is kept in memory.

There are helpful macros that wrap `$` for specific elements like `div`,
`button`, `input`, etc. With these, the library provides a simple DSL for
constructing elements.

To create an app, you need a function that constructs these "DOM expressions,"
which will be called via `patch` function with the root node of the app when
necessary. State management is left as an exercise to the reader.

### API

See `town.lilac.dom` docstrings.

### Example

```clojure
(ns my-app.main
  (:require
   [town.lilac.dom :as d]))

(def *state (atom {:text "bonjour"}))

(defn on-change
  [text]
  (swap! *state assoc :text text))

(defn app
  [{:keys [text]} on-change]
  (d/div
   {:style {:fontFamily "sans-serif"}}
   (d/input
    {:style {:border "1px solid red"}
     :oninput (fn [e]
                (on-change (.. e -target -value)))
     :value text
     & (when (= "hello" text)
         {:style {:border "1px solid blue"}})})
   (d/div (d/text text))))

(defn render!
  [state]
  (d/patch (js/document.getElementById "root")
           #(app state on-change)))

(add-watch *state :render (fn [_ _ _ state] (render! state)))

(render! @*state)

(comment
  (swap! *state assoc :text "hi"))
```

### How it works

> Side effects may include...

[incremental-dom](https://github.com/google/incremental-dom) operates via side-
effects; when you call `$` or a specific DOM macro like `div` via this library,
the library does some internal book keeping to track what elements contain others
via the order of `open` and `close` calls.

```clojure
($ "div" {:style {:color "red"}} (text "hello"))

;; emits something akin to
(open "div" {:color "red"})
(text "hello")
(close "div")
```

The `close` call will return the `HTMLElement` node for the div with the HTML
`Text` node as a child of it.

After the function you passed to `patch` returns, it will take the tree of
elements constructed, diff the resulting tree with what is present within the
root element, and update the root with nodes that have changed.

#### What this means

*You do not need to return an element for it to be added to the result*

```clojure
(defn app
  []
  (d/div
   (d/text "hello ")
   (let [text (d/text "side effects")]
     ;; no return value
     nil)
   (d/text "!")))

(d/patch root app)
;; results in <div>"hello " "side effects" "!"</div> added to the page
```

### Dynamic attributes

The `$` macro needs to determine whether an argument passed to it is a child in
order to place the `open` and `close` calls correctly. The heuristic it uses is:
anything that isn't a map literal in the first position is a child. So:

```clojure
($ "div" (foo) [bar] baz)

;; emits
(open "div")
(foo)
[bar]
baz
(close "div")
```

This complicates things when you want to pass in a dynamic map of attributes

```clojure
(let [attrs {:style {:color "red"}}]
  ($ "div" (merge {:id "asdf"} attrs) foo bar baz))

;; OOPS! emits
(let [attrs {:style {:color "red"}}]
  (open "div")
  (merge {:id "asdf"} attrs)
  foo
  bar
  baz
  (close "div"))
```

Ideally, we would detect that `attrs` is in fact a map and pass it to the `open`
call. However, this isn't possible to do at compile time in all cases.

Instead, the `$` and other DOM element macros accept a special attribute, `&` or
`:&` which will merge any static attributes you pass in with ones that are
passed in dynamically.

Here is the **correct code**:

```clojure
(let [attrs {:style {:color "red"}}]
  ($ "div" {:id "asdf" & attrs} foo bar baz))

;; emits
(let [attrs {:style {:color "red"}}]
  (open "div" (merge {:id "asdf"} attrs))
  foo
  bar
  baz
  (close "div"))
```
