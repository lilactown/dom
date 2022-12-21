# town.lilac/dom

An extremely simple library for declaratively creating and updating web pages.

It is a thin wrapper around google's extremely stable
[incremental-dom](https://github.com/google/incremental-dom) library.

## Install

The library is available via git deps

```clojure
town.lilac/dom {:git/url "https://github.com/lilactown/dom"
                :git/sha "8ab889ad9ae071b25e4e001c0ac9dac0c32a0234"}
```

## Usage

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
     :value text})
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
