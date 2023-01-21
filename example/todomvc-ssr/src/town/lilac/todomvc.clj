(ns town.lilac.todomvc
  (:require
   [aleph.http :as http]
   [aleph.netty :as netty]
   [manifold.stream :as s]
   [manifold.deferred :as d]
   [muuntaja.core :as m]
   [reitit.dev.pretty :as pretty]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.coercion.spec]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.parameters :as parameters]
   [town.lilac.dom.server :as dom]))

(declare server)

(when-not (instance? clojure.lang.Var$Unbound server)
  (prn "closing")
  (.close server)
  (netty/wait-for-close server))

(def db (atom {:todos [{:label "Write code"
                        :id (gensym "todo")
                        :completed true}
                       {:label "Dishes"
                        :id (gensym "todo")
                        :completed false}]}))


(defn todo-list
  [{:keys [filter]}]
  (dom/section
   {:class "main"}
   (dom/input {:id "toggle-all" :class "toggle-all" :type "checkbox"})
   (dom/label {:for "toggle-all"} (dom/text "Mark all as complete"))
   (dom/ul
    {:class "todo-list"}
    (doseq [todo (:todos @db)
            :when (case filter
                    :all true
                    :active (not (:completed todo))
                    :complete (:completed todo))]
      (dom/li
       {:class "todo" :id (:id todo)}
       (dom/div
        {:class "view"}
        (dom/input {:class "toggle"
                    :type "checkbox"
                    :checked (:completed todo)
                    :name "completed"
                    :hx-put (str "partial/todo/" (:id todo))
                    :hx-target ".todoapp"
                    :hx-swap "outerHTML"})
        (dom/label
         {:hx-get (str "partial/todo/" (:id todo) "/edit")
          :hx-target (str "#" (:id todo))
          :hx-swap "outerHTML"}
         (dom/text (:label todo)))
        (dom/button {:class "destroy"
                     :hx-delete (str "partial/todo/" (:id todo))
                     :hx-target ".todoapp"
                     :hx-swap "outerHTML"})))))))

(defn app
  [{:keys [filter]}]
  (dom/section
    {:class "todoapp"
     :hx-vals (str "{\"filter\": \"" (name filter) "\"}")}
    (dom/header
     {:class "header"}
     (dom/h1 (dom/text "todos"))
     (dom/form
      {:action "/todo"
       :method "POST"
       ;; JS enabled
       :hx-post "/partial/todo"
       :hx-target ".todoapp"
       :hx-swap "outerHTML"}
      (dom/input {:type "text"
                  :class "new-todo"
                  :name "label"
                  :autofocus true
                  :autocomplete "off"
                  :placeholder "What needs to be done?"})))
    (todo-list {:filter filter})
    (dom/footer
     {:class "footer"}
     (dom/span
      {:class "todo-count"}
      (dom/strong (dom/text (str (count (:todos @db)) " item(s) left"))))
     (dom/ul
      {:class "filters"}
      (dom/li (dom/a
               {:class (when (= filter :all) "selected")
                :href "?filter=all"
                :hx-get "partial/todo?filter=all"
                :hx-target ".todoapp"
                :hx-swap "outerHTML"}
               (dom/text "All")))
      (dom/li (dom/a
               {:class (when (= filter :active) "selected")
                :href "?filter=active"
                :hx-get "partial/todo?filter=active"
                :hx-target ".todoapp"
                :hx-swap "outerHTML"}
               (dom/text "Active")))
      (dom/li (dom/a
               {:class (when (= filter :complete) "selected")
                :href "?filter=complete"
                :hx-get "partial/todo?filter=complete"
                :hx-target ".todoapp"
                :hx-swap "outerHTML"}
               (dom/text "Complete"))))
     (dom/button
      {:class "clear-completed"
       :hx-post "partial/clear"
       :hx-target ".todoapp"
       :hx-swap "outerHTML"}
      (dom/text "Clear completed")))))

(defn page
  [{:keys [filter]}]
  (dom/html5)
  (dom/head
   (dom/meta {:charset "UTF-8"})
   (dom/title "TodoMVC")
   (dom/link {:rel "stylesheet" :href "assets/todomvc-common/base.css"})
   (dom/link {:rel "stylesheet" :href "assets/todomvc-app-css/index.css"})
   (dom/script {:src "https://unpkg.com/htmx.org@1.8.5"})
   #_(dom/script {:type "module" :src "https://cdn.skypack.dev/@hotwired/turbo"}))
  (dom/body
   (app {:filter filter})
   (dom/script {:src "assets/todomvc-common/base.js"})))

(defn page-handler
  [req]
  (let [html (s/stream)
        filter (get-in req [:params "filter"] "all")]
    (d/future
      (dom/render-stream html #(page {:filter (keyword filter)}))
      (s/close! html))
    {:status 200
     :headers {"content-type" "text/html"}
     :body html}))

(defn new-todo-handler
  [req]
  (let [{:strs [label]} (:params req)]
    (swap! db update :todos conj {:id (gensym "todo")
                                  :label label
                                  :completed false})
    {:status 303
     :headers {"Location" "/"}}))

(defn partial-todos-handler
  [req]
  (let [html (s/stream)
        ;; hx-vals adds as params to the end of the URL
        filter (first (get-in req [:params "filter"] "all"))]
    (d/future
      (dom/render-stream html #(app {:filter (keyword filter)}))
      (s/close! html))
    {:status 200
     :headers {"content-type" "text/html"}
     :body html}))

(defn partial-new-todo-handler
  [req]
  (let [{:strs [label]} (:params req)
        {:strs [filter]} (:params req)
        html (s/stream)]
    (swap! db update :todos conj {:id (gensym "todo")
                                  :label label
                                  :completed false})
    (d/future
      (dom/render-stream html #(app {:filter (keyword filter)}))
      (s/close! html))
    {:status 200
     :headers {"content-type" "text/html"}
     :body html}))

(defn partial-delete-todo-handler
  [req]
  (let [{:keys [id]} (:path-params req)
        {:strs [filter]} (:params req)
        html (s/stream)]
    (swap! db update :todos (fn [todos]
                              (->> todos
                                   (remove #(= id (str (:id %))))
                                   (vec))))
    (d/future
      (dom/render-stream html #(app {:filter (keyword filter)}))
      (s/close! html))
    {:status 200
     :headers {"content-type" "text/html"}
     :body html}))

(defn partial-update-todo-handler
  [req]
  (let [{:keys [id]} (:path-params req)
        {:strs [filter completed label]} (:params req)
        html (s/stream)]
    (swap! db update :todos
           (fn [todos]
             (mapv
              (fn [todo]
                (if (= id (str (:id todo)))
                  (cond-> todo
                    (nil? label) (assoc :completed (case completed
                                                     "on" true
                                                     false))
                    (some? label) (assoc :label label))
                  todo))
              todos)))
    (d/future
      (dom/render-stream html #(app {:filter (keyword filter)}))
      (s/close! html))
    {:status 200
     :headers {"content-type" "text/html"}
     :body html}))

(defn edit-todo
  [id]
  (dom/li
   {:class "todo editing" :id id}
   (dom/form
    {:hx-put (str "partial/todo/" id)
     :hx-target ".todoapp"
     :hx-swap "outerHTML"}
    (dom/input {:class "edit"
                :type "text"
                :name "label"
                :value (->> @db
                            (:todos)
                            (filter #(= id (str (:id %))))
                            (first)
                            (:label))}))))

(defn partial-edit-todo
  [req]
  (let [{:keys [id]} (:path-params req)
        html (s/stream)]
    (prn :edit)
    (d/future
      (dom/render-stream html #(edit-todo id))
      (s/close! html))
    {:status 200
     :headers {"content-type" "text/html"}
     :body html}))

(defn partial-clear-handler
  [req]
  (let [html (s/stream)]
    (swap! db update :todos (fn [todos]
                              (->> todos
                                   (filter #(not (:completed %)))
                                   (vec))))
    (d/future
      (dom/render-stream
       html #(app {:filter (keyword
                            (get-in req [:params "filter"]))}))
      (s/close! html))
    {:status 200
     :headers {"content-type" "text/html"}
     :body html}))


(def router
  (ring/router
   [["/" {:get page-handler}]
    ["/todo" {:post new-todo-handler}]
    ["/partial"
     [["/todo" {:get partial-todos-handler
                :post partial-new-todo-handler}]
      ["/todo/:id" {:delete partial-delete-todo-handler
                    :put partial-update-todo-handler}]
      ["/todo/:id/edit" {:get partial-edit-todo}]
      ["/clear" {:post partial-clear-handler}]]]
    ["/assets/*" (ring/create-resource-handler)]]
   {;;:reitit.middleware/transform dev/print-request-diffs ;; pretty diffs
    ;;:validate spec/validate ;; enable spec validation for route data
    ;;:reitit.spec/wrap spell/closed ;; strict top-level validation
    :exception pretty/exception
    :data {:coercion reitit.coercion.spec/coercion
           :muuntaja m/instance
           :middleware [;; query-params & form-params
                        parameters/parameters-middleware
                        ;; content-negotiation
                        muuntaja/format-negotiate-middleware
                        ;; encoding response body
                        muuntaja/format-response-middleware
                        ;; exception handling
                        (exception/create-exception-middleware
                         {::exception/default (partial exception/wrap-log-to-console exception/default-handler)})
                        ;; decoding request body
                        muuntaja/format-request-middleware
                        ;; coercing response bodys
                        coercion/coerce-response-middleware
                        ;; coercing request parameters
                        coercion/coerce-request-middleware]}}))

(def server
  (http/start-server
   (ring/ring-handler router)
   {:port 9090
    :shutdown-timeout 0}))
