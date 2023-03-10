(ns town.lilac.dom.parse
  "Runtime parsing of data into incremental-dom patches.
  Supports both EDN and JSON formats. "
  (:require
   ["incremental-dom" :as dom]
   [goog.object :as gobj]))

(defn edn
  "Accepts a tree of EDN data, parses and executes each element as
  incremental-dom commands.

  Example:
  ($ \"div\" {:style {:color \"red\"}} (text \"hi\"))"
  [data]
  (when (seq data)
    (let [[hd & more] data]
      (case hd
        text (apply dom/text more)
        $ (let [tag (first more)
                [attrs children] (if (map? (second more))
                                   [(second more) (drop 2 more)]
                                   [nil (next more)])]
            (dom/elementOpenStart tag (:key attrs) nil)
            (doseq [[k v] (dissoc attrs :key)]
              (dom/attr (name k) (clj->js v)))
            (dom/elementOpenEnd tag)
            (doseq [child children]
              (edn child))
            (dom/elementClose tag))
        nil))))

(defn json
  "Accepts a tree of JSON data, parses and executes each element as
  incremental-dom commands.

  Example:
  #js [\"$\" \"div\" #js {:style #js {:color \"red\"}} #js [\"text\" \"hi\"]]"
  [data]
  (when (array? data)
    (let [hd (aget data 0)
          more (.slice data 1)]
      (case hd
        "text" (.apply dom/text nil more)
        "$" (let [tag (aget more 0)
                  attrs? (object? (aget more 1))
                  attrs (when attrs? (aget more 1))
                  children (if attrs? (.slice more 2) (.slice more 1))]
              (dom/elementOpenStart tag (gobj/get attrs "key") nil)
              ;; TODO clone attrs and dissoc?
              (gobj/forEach attrs (fn [v k _o]
                                    (when (not (identical? "key" k))
                                      (dom/attr k v))))
              (dom/elementOpenEnd tag)
              (when children
                (.forEach children (fn [v] (json v))))
              (dom/elementClose tag))
        nil))))

(comment
  (time
   (dom/patch
    (js/document.getElementById "root")
    #(edn
      '($ "div" (text "hi")
          ($ "button" {:onclick "alert(\"bye\")"}
             (text "bye"))))))

  (time
   (dom/patch
    (js/document.getElementById "root")
    #(json #js ["$" "div" #js {:style #js {:color "red"}}
                #js ["text" "red"]])))

  (require '[town.lilac.dom])

  (time
   (dom/patch
    (js/document.getElementById "root")
    #(town.lilac.dom/$
      "div"
      (town.lilac.dom/text "bonjour")
      (town.lilac.dom/$
       "button"
       {:onclick "alert(\"bye\")"}
       (town.lilac.dom/text "hello"))))))
