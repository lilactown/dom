(ns town.lilac.dom.server
  (:refer-clojure :exclude [map meta time])
  (:require
   [manifold.stream :as s]
   [town.lilac.dom.server.attrs :as attrs]))

(def ^:dynamic *buffer* nil)

(defprotocol IBuffer
  (-put! [b parts])
  (-await! [b]))

(extend-type StringBuilder
  IBuffer
  (-put! [sb parts]
    (doseq [p parts]
      (.append sb p))
    sb))

(defrecord ManifoldStream [sink]
  IBuffer
  (-put! [_ parts]
    (s/put! sink (apply str parts))))

(defn put!
  [& parts]
  (-put! *buffer* parts))

(defn render-string
  [f]
  (str
   (binding [*buffer* (or *buffer* (StringBuilder.))]
     (f))))

(defn render-stream
  [sink f]
  (binding [*buffer* (->ManifoldStream sink)]
    (f))
  )

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
  (put! "<" tag (attrs/attrs->str attrs) ">"))

(defn open
  [tag _key attrs]
  (put! "<" tag (attrs/attrs->str attrs) ">"))

(defn close
  [tag]
  (put! "</" tag ">"))

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
  (put! (escape-html s)))

(defn html5
  []
  (put! "<!DOCTYPE html>"))

(defn html-blob
  "Create an HTML blob out of string `content`, rendering it directly on the
  page when patched."
  [content]
  (put! content))

(comment
  (render-string #($ "div" (text "hi")))
  ;; => "<div>hi</div>"

  (render-string #(div (text "hi")))
  ;; => "<div>hi</div>"

  (let [s (s/stream)]
    (render-stream s #($ "div" (text "hi")))
    (s/close! s)
    (s/stream->seq s))
  )
