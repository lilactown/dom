(ns town.lilac.todomvc
  (:require
   [town.lilac.dom :as dom]
   [town.lilac.flex :as flex]
   [town.lilac.flex.dom :as fdom]))

(defn todo-item
  [{:keys [title id completed?]}
   {:keys [on-toggle on-delete on-edit]}]
  (let [editing? (flex/source false)]
    (fdom/track
     (dom/li
      {:class (if @editing? "todo editing" "todo") :id id :key id}
      (dom/div
       {:class "view"}
       (let [el (dom/input {:class "toggle"
                            :type "checkbox"
                            :checked (when completed? true)
                            :name "completed"
                            :onchange #(on-toggle)})]
         (when (not= completed? (= "on" (.. el -checked)))
           (set! (.. el -checked) (case completed?
                                    true "on"
                                    false nil))))
       (dom/label
        {:ondblclick #(editing? true)}
        (dom/text title))
       (dom/button {:class "destroy"
                    :onclick #(on-delete)}))
      (dom/form
       {:onsubmit (fn [e]
                    (.preventDefault e)
                    (on-edit (.. e -target -title -value)))}
       (dom/input {:class "edit"
                   :type "text"
                   :name "title"
                   :autofocus true
                   :value title}))))))

(defn todo-list
  [{:keys [filter todos on-toggle on-delete on-toggle-all on-edit]}]
  (dom/section
   {:class "main"}
   (dom/input {:id "toggle-all"
               :class "toggle-all"
               :type "checkbox"
               :name "toggle-all"
               :checked (when (every? #(:completed? %) todos) true)
               :onchange #(on-toggle-all (.. % -target -checked))})
   (dom/label {:for "toggle-all"} (dom/text "Mark all as complete"))
   (dom/ul
    {:class "todo-list"}
    (doseq [todo todos
            :when (case filter
                    :all true
                    :active (not (:completed? todo))
                    :complete (:completed? todo))]
      (todo-item todo {:on-toggle #(on-toggle (:id todo))
                       :on-delete #(on-delete (:id todo))
                       :on-edit #(on-edit (:id todo) %)})))))

(defn app
  []
  (let [ids (flex/source 2)
        todos (flex/source [{:id 0
                             :title "Write code"
                             :completed? true}
                            {:id 1
                             :title "Dishes"
                             :completed? false}])
        list-filter (flex/source :all)]
    (dom/section
     {:class "todoapp"}
     (dom/header
      {:class "header"}
      (dom/h1 (dom/text "todos"))
      (dom/form
       {:onsubmit (fn [^js e]
                    (.preventDefault e)
                    (flex/batch
                     (let [id @ids]
                       (ids inc)
                       (todos #(conj % {:id id
                                        :title (.. e -target -title -value)
                                        :completed? false}))))
                    (set! (.. e -target -title -value) ""))}
       (dom/input {:type "text"
                   :class "new-todo"
                   :name "title"
                   :autofocus true
                   :autocomplete "off"
                   :placeholder "What needs to be done?"})))
     (fdom/track
      (todo-list
       {:filter @list-filter
        :todos @todos
        :on-toggle (fn [id]
                     (todos (fn [todos]
                              (mapv
                               #(if (= id (:id %))
                                  (update % :completed? not)
                                  %)
                               todos))))
        :on-delete (fn [id]
                     (todos (fn [todos]
                              (->> todos
                                   (filter #(not= id (:id %)))
                                   vec))))
        :on-toggle-all (fn [v]
                         (todos
                          (fn [todos]
                            (mapv #(assoc % :completed? v) todos))))
        :on-edit (fn [id v]
                   (todos
                    (fn [todos]
                      (mapv
                       #(if (= id (:id %))
                          (assoc % :title v)
                          %)
                       todos))))}))
     (dom/footer
      {:class "footer"}
      (fdom/track
       (dom/span
        {:class "todo-count"}
        (dom/strong
         (dom/text
          (str (count (filter #(not (:completed? %)) @todos))
               " item(s) left")))))
      (fdom/track
       (dom/ul
        {:class "filters"}
        (dom/li (dom/a
                 {:class (if (= @list-filter :all) "selected" "")
                  :href "#"
                  :onclick (fn [e]
                             (.preventDefault e)
                             (list-filter :all))}
                 (dom/text "All")))
        (dom/li (dom/a
                 {:class (if (= @list-filter :active) "selected" "")
                  :href "#"
                  :onclick (fn [e]
                             (.preventDefault e)
                             (list-filter :active))}
                 (dom/text "Active")))
        (dom/li (dom/a
                 {:class (if (= @list-filter :complete) "selected" "")
                  :href "#"
                  :onclick (fn [e]
                             (.preventDefault e)
                             (list-filter :complete))}
                 (dom/text "Complete")))))
      (dom/button
       {:class "clear-completed"
        :onclick (fn [_e]
                   (todos
                    (fn [todos]
                      (->> todos
                           (filter #(not (:completed? %)))
                           (vec)))))}
       (dom/text "Clear completed"))))))

(defn start!
  []
  (dom/patch (js/document.getElementById "root") app))

(start!)
