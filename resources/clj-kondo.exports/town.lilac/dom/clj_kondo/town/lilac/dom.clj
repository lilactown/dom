(ns clj-kondo.town.lilac.dom
  (:require
   [clj-kondo.hooks-api :as api]))


(defn $
  "Macro analysis for `town.lilac.dom/$`."
  [{:keys [node]}]
  (let [[fn-sym & body] (-> node :children)
        [component-sym body] [(first body) (next body)]
        [old-props body] (if (api/map-node? (first body))
                           [(-> body first :children) (next body)]
                           [nil body])
        children body
        new-props (when old-props
                    (->> old-props
                         (map
                          #(if (cond-> (api/sexpr %) symbol? (= '&))
                             (api/keyword-node :&)
                             %))
                         api/map-node))
        expanded (api/list-node
                  (list* fn-sym component-sym new-props children))]
    {:node (with-meta expanded (meta node))}))


(defn dom
  "Macro analysis for `town.lilac.dom` macros."
  [{:keys [node]}]
  (let [[fn-sym & body] (-> node :children)
        [old-props body] (if (api/map-node? (first body))
                           [(-> body first :children) (next body)]
                           [nil body])
        children body
        new-props (when old-props
                    (->> old-props
                         (map
                          #(if (cond-> (api/sexpr %) symbol? (= '&))
                             (api/keyword-node :&)
                             %))
                         api/map-node))
        expanded (api/list-node
                  (list* fn-sym new-props children))]
    {:node (with-meta expanded (meta node))}))


(defn async
  [{:keys [node]}]
  (let [body (:children node)
        fallback (last body)
        body (rest (drop-last body))]
    (if (= 'fallback (first (api/sexpr fallback)))
      {:node (with-meta
               (api/list-node
                `(try
                   ~@body
                   ~(api/list-node
                     `(catch js/Promise p#
                        ~@(rest (:children fallback))))))
               (meta node))}
      (throw (ex-info "Last expr in async must be fallback" {:node node})))))
