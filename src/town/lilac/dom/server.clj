(ns town.lilac.dom.server)

(def ^:dynamic *sb* nil)

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
    (.append sb ">")
    sb))

(defn open
  [tag _key attrs]
  (let [^StringBuilder sb *sb*]
    (.append sb "<")
    (.append sb tag)
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
                      [nil attrs])]
    (if (contains? void-tags tag)
      `(void ~tag ~key ~attrs)
      `(do (open ~tag ~key ~attrs)
           ~@children
           (close ~tag)))))

(defn text
  [s]
  (.append ^StringBuilder *sb* s)
  s)

(defn patch
  [f]
  (binding [*sb* (or *sb* (StringBuilder.))]
    (f)))


(str (patch #($ "div" (text "hi"))))
;; => "<div>hi</div>"
