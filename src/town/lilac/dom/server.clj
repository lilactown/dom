(ns town.lilac.dom.server
  (:refer-clojure :exclude [map meta time])
  (:require [town.lilac.dom.server.attrs :as attrs]))

(def ^:dynamic *sb* nil)

(defn render
  [f]
  (str
   (binding [*sb* (or *sb* (StringBuilder.))]
     (f))))

(def void-tags
  #{"area"
    "base"
    "br"
    "col"
    "embed"
    "hr"
    "img"
    "input"
    "link"
    "meta"
    "param"
    "source"
    "track"
    "wbr"})

(defn void
  [tag _key attrs]
  (let [^StringBuilder sb *sb*]
    (.append sb "<")
    (.append sb tag)
    (.append sb (attrs/attrs->str attrs))
    (.append sb ">")
    sb))

(defn open
  [tag _key attrs]
  (let [^StringBuilder sb *sb*]
    (.append sb "<")
    (.append sb tag)
    (.append sb (attrs/attrs->str attrs))
    (.append sb ">")
    sb))

(defn close
  [tag]
  (let [^StringBuilder sb *sb*]
    (.append sb "</")
    (.append sb tag)
    (.append sb ">")
    sb))

(defmacro $
  [tag & args]
  (let [[attrs children] (if (map? (first args))
                           [(first args) (rest args)]
                           [nil args])
        [key attrs] (if-let [key (:key attrs)]
                      [key (dissoc attrs :key)]
                      [nil attrs])
        attrs (cond
                (contains? attrs :&) `(merge ~(dissoc attrs :&)
                                             ~(:& attrs))
                (contains? attrs '&) `(merge ~(dissoc attrs '&)
                                             ~('& attrs))
                :else attrs)]
    (if (contains? void-tags tag)
      `(void ~tag ~key ~attrs)
      `(do (open ~tag ~key ~attrs)
           ~@children
           (close ~tag)))))


(declare input textarea option select a abbr address area article aside audio b base bdi
 bdo big blockquote body br button canvas caption cite code col colgroup data datalist
 dd del details dfn dialog div dl dt em embed fieldset figcaption figure footer form
 h1 h2 h3 h4 h5 h6 head header hr html i iframe img ins kbd keygen label legend li link
 main map mark menu menuitem meta meter nav noscript object ol optgroup output p param
 picture pre progress q rp rt ruby s samp script section small source span strong style
 sub summary sup table tbody td tfoot th thead time title tr track u ul var video wbr
 circle clipPath ellipse g line mask path pattern polyline rect svg text defs
 linearGradient polygon radialGradient stop tspan)

(def tags
  '[input textarea option select a abbr address area article aside audio
    b base bdi bdo big blockquote body br button canvas caption cite code col
    colgroup data datalist dd del details dfn dialog div dl dt em embed fieldset
    figcaption figure footer form h1 h2 h3 h4 h5 h6 head header hr html i iframe
    img ins kbd keygen label legend li link main map mark menu menuitem meta
    meter nav noscript object ol optgroup output p param picture pre progress q
    rp rt ruby s samp script section small source span strong style sub summary
    sup table tbody td tfoot th thead time title tr track u ul var video wbr])

(defn gen-tag
  [tag]
  `(defmacro ~tag [& args#]
     `($ ~(str '~tag) ~@args#)))

(defmacro gen-tags
  []
  `(do
     ~@(for [tag tags]
         (gen-tag tag))))

(gen-tags)


(defn escape-html
  [^String text]
  (.. text
    (replace "&"  "&amp;")
    (replace "<"  "&lt;")
    (replace ">"  "&gt;")
    (replace "\"" "&quot;")
    (replace "'" "&apos;")))

(defn text
  [s]
  (doto (escape-html s)
    (->> (.append ^StringBuilder *sb*))))

(defn html5
  []
  (.append ^StringBuilder *sb* "<!DOCTYPE html>"))

(defn html-blob
  "Create an HTML blob out of string `content`, rendering it directly on the
  page when patched."
  [content]
  (.append ^StringBuilder *sb* content))

(comment
  (render #($ "div" (text "hi")))
  ;; => "<div>hi</div>"

  (render #(div (text "hi")))
  ;; => "<div>hi</div>"
  )
