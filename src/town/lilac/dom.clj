(ns town.lilac.dom
  "Macros for creating DOM expressions. See `$` for usage.
  Additional macros like `div`, `input`, `button` allow quick & easy creation of
  specific tags.

  The code emitted by `$` and friends is side effecting. You do not need to keep
  the value returned by `$` or any of the specific DOM macros.
  \"incremental-dom\" keeps track of the elements created and diffs the result
  against the DOM nodes on the page during `patch`.

  Calling `$` and friends outside of a `patch` call is a runtime error."
  (:refer-clojure :exclude [map meta time]))

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

(defmacro $
  "Core macro for creating DOM expressions. Emits code that uses Google's
  \"incremental-dom\" library to create, diff and patch the DOM nodes on the
  page.

  `tag` (string) is the HTML tag you want to open. Optionally, a map of
  attributes may be passed in the second position to configure the resulting DOM
  node.

  For non-void tags, any other type and/or any additional arguments are emitted
  between the open and close tag calls. E.g. ($ \"div\" ($ \"input\")) will
  place the input inside of the div.

  Void tags (i.e. tags that do not close, for instance \"input\") do not emit
  any of its args."
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
      `(do
        (open ~tag ~key ~attrs)
        ~@children
        (close ~tag)))))

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

(defmacro buffer
  [& body]
  `(binding [*buffer* (cljs.core/array)]
     ~@body
     (flush!)))

(defmacro try
  [& body]
  (let [catch (last body)
        body (drop-last body)]
    `(try
      (buffer ~@body)
      ~catch)))

;; async runtime

(defmacro async
  [& body]
  (let [fallback (last body)
        body (drop-last body)
        fn-sym (gensym "async-fn")]
    (when (not= 'fallback (first fallback))
      (throw
       (ex-info "async expr requires (fallback ,,,) as last expression in body"
                {:body body})))
    `((fn ~fn-sym []
        (prn ~fn-sym)
        (let [buffer# (cljs.core/array)
              parent# (get-current-element)]
          (try
            (buffer ~@body)
            (catch js/Promise e#
              (let [fallback-id# (gensym "fallback")
                    ;; TODO assert that fallback is a single element
                    el# ~@(rest fallback)]
                (.then e# (fn [result#]
                            (patch el# ~fn-sym)))))))))))
