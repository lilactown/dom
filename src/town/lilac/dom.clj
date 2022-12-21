(ns town.lilac.dom
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
  [tag & args]
  (let [[attrs children] (if (map? (first args))
                           [(first args) (rest args)]
                           [nil args])
        [key attrs] (if-let [key (:key attrs)]
                      [key (dissoc attrs :key)]
                      [nil attrs])]
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
