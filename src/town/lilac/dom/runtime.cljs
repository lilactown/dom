(ns town.lilac.dom.runtime
  (:require
   ["incremental-dom" :as dom]
   [goog.object :as gobj]
   [town.lilac.dom.parse :as parse]))

(defn ^:export exec
  [o]
  (let [target (gobj/get o "target")
        expr (gobj/get o "expr")]
    (dom/patch (js/document.querySelector target) #(parse/json expr))))
