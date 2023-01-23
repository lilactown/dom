(ns town.lilac.dom.server
  (:refer-clojure :exclude [map meta time use])
  (:require
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [town.lilac.dom.server.attrs :as attrs])
  (:import [manifold.deferred IDeferred]
           [clojure.lang ExceptionInfo]))

(def ^:dynamic *buffer* nil)

(defprotocol IBuffer
  (-put! [b parts])
  (-flush! [b])
  (-await! [b d f]))

(extend-type StringBuilder
  IBuffer
  (-put! [sb parts]
    (doseq [p parts]
      (.append sb p))
    sb)
  (-flush! [sb]
    (.toString sb))
  (-await! [_ d f]
    #_(prn :await/str)
    d))

(deftype TransientBuffer [^:volatile-mutable t]
  IBuffer
  (-put! [_ parts]
    (set! t (conj! t (apply str parts))))
  (-flush! [_]
    (persistent! t))
  (-await! [_ d f]
    #_(prn :await/transient)))

(deftype ManifoldStream [sink
                         ^:volatile-mutable deferred-queue
                         ^:volatile-mutable async-id]
  IBuffer
  (-put! [_ parts]
    (s/put! sink (apply str parts)))
  (-flush! [_]
    (s/stream->seq sink))
  (-await! [_ d f]
    (set! async-id (inc async-id))
    (set! deferred-queue (conj! deferred-queue [async-id d f]))
    async-id))

(defn put!
  [& parts]
  (-put! *buffer* parts))

(defn render-string
  [f]
  (binding [*buffer* (or *buffer* (StringBuilder.))]
    (f)
    (-flush! *buffer*)))

(defn render-stream
  [sink f]
  (let [deferred-queue (transient [])]
    (binding [*buffer* (->ManifoldStream sink deferred-queue 0)]
      (f))
    (let [dq (persistent! deferred-queue)]
      (when (seq dq)
        @(apply
          d/zip
          (for [[id d f] dq]
            (d/chain
             d
             (fn [_]
               (s/put! sink (str "<div hidden id=\"TLDA:" id "\">"))
               (let [d (render-stream sink f)]
                 (s/put! sink "</div>")
                 (s/put!
                  sink
                  (str "<script>"
                       "$TLD(\"TLDB:" id "\", \"TLDA:" id "\")"
                       "</script>"))
                 d)))))))))

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

(defn use
  [v]
  (if (instance? IDeferred v)
    (throw (ex-info "" {::use v}))
    v))


(declare
 input textarea option select a abbr address area article aside audio b base bdi
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
    sup table tbody td tfoot th thead time title tr track u ul var video wbr
    template])

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


(defmacro buffer
  [& body]
  `(let [b# (->TransientBuffer (transient []))]
     (binding [*buffer* b#]
       ~@body)
     (doseq [p# (-flush! b#)]
       (put! p#))))


(defmacro async
  [& body]
  (let [fallback (last body)
        body (drop-last body)
        fn-sym (gensym "async")]
    ;; Poor persons continuation:
    ;; 1. Wrap body in a thunk
    ;; 2. Call the thunk
    ;; 3. If any deferreds get thrown via `use`, then render fallback
    ;; 4. Put deferred in the async queue
    ;; 5. When deferred resolves, re-run this thunk
    `((fn ~fn-sym
        []
        (try
          (buffer ~@body)
          (catch ExceptionInfo e#
            (if-let [d# (::use (ex-data e#))]
              (let [id# (-await! *buffer* d# ~fn-sym)]
                (put! "<!--$?-->")
                (template {:id (str "TLDB:" id#)})
                ~@(rest fallback)
                (put! "<!--/$-->"))
              (throw e#))))))))


(comment
  (render-string #($ "div" (text "hi")))
  ;; => "<div>hi</div>"

  (render-string #(div (text "hi")))
  ;; => "<div>hi</div>"

  (let [s (s/stream)]
    (render-stream s #($ "div" (text "hi")))
    (s/close! s)
    (s/stream->seq s))
  ;; => ("<div>" "hi" "</div>")

  (def greeting nil)

  (defn greet
    []
    (or greeting (d/chain
                  (d/success-deferred "hi")
                  #(alter-var-root #'greeting (constantly %)))))

  (defn page
    []
    (html5)
    (head
     (title (text "testing")))
    (body
     (div
      (button (text "click me"))
      (async
       (text
        (use (greet)))
       (button (text (use (greet))))
       (fallback
        (span (text "loading...")))))))

  (render-string page)
  ;; => "<div><button>click me</button><span>loading...</span></div>"


  (clojure.core/time
   (let [s (s/stream)]
     (alter-var-root #'greeting (constantly nil))
     (render-stream s page)
     (s/close! s)
     (s/stream->seq s)))
  )
