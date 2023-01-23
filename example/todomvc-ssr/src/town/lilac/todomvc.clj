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

(def db (atom {:todos [{:label "Write code"
                        :id (gensym "todo")
                        :completed true}
                       {:label "Dishes"
                        :id (gensym "todo")
                        :completed false}]}))

(defn todo-item
  [{:keys [label id completed editing?]}]
  (dom/li
   {:class (if editing? "todo editing" "todo") :id id}
   (dom/div
    {:class "view"}
    (dom/input {:class "toggle"
                :type "checkbox"
                :checked completed
                :name "completed"
                :hx-put (str "partial/todo/" id)
                :hx-target "#root"})
    (dom/label
     {:hx-get (str "partial/todo/" id "/edit")
      :hx-trigger "dblclick"
      :hx-target (str "#" id)
      :hx-swap "outerHTML"}
     (dom/text label))
    (dom/button {:class "destroy"
                 :hx-delete (str "partial/todo/" id)
                 :hx-target "#root"}))
   (dom/form
     {:hx-put (str "partial/todo/" id)
      :hx-target "#root"}
     (dom/input {:class "edit"
                 :type "text"
                 :name "label"
                 :hx-get "partial/todo"
                 :hx-target "#root"
                 :hx-trigger "blur"
                 :autofocus true
                 :value label}))))

(defn todo-list
  [{:keys [filter]}]
  (dom/section
   {:class "main"}
   (dom/input {:id "toggle-all"
               :class "toggle-all"
               :type "checkbox"
               :name "toggle-all"
               :hx-post "partial/toggle-all"
               :hx-target "#root"
               :checked (every? #(:completed %) (:todos @db))})
   (dom/label {:for "toggle-all"} (dom/text "Mark all as complete"))
   (dom/ul
    {:class "todo-list"}
    (doseq [todo (:todos @db)
            :when (case filter
                    :all true
                    :active (not (:completed todo))
                    :complete (:completed todo))]
      (todo-item todo)))))

(defn app
  [{:keys [filter auto-focus?]}]
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
       :hx-target "#root"}
      (dom/input {:type "text"
                  :class "new-todo"
                  :name "label"
                  :autofocus auto-focus?
                  :autocomplete "off"
                  :placeholder "What needs to be done?"})))
    (todo-list {:filter filter})
    (dom/footer
     {:class "footer"}
     (dom/span
      {:class "todo-count"}
      (dom/strong
       (dom/text
        (str (count (clojure.core/filter #(not (:completed %)) (:todos @db)))
             " item(s) left"))))
     (dom/ul
      {:class "filters"}
      (dom/li (dom/a
               {:class (when (= filter :all) "selected")
                :href "?filter=all"
                :hx-get "partial/todo?filter=all"
                :hx-target "#root"}
               (dom/text "All")))
      (dom/li (dom/a
               {:class (when (= filter :active) "selected")
                :href "?filter=active"
                :hx-get "partial/todo?filter=active"
                :hx-target "#root"}
               (dom/text "Active")))
      (dom/li (dom/a
               {:class (when (= filter :complete) "selected")
                :href "?filter=complete"
                :hx-get "partial/todo?filter=complete"
                :hx-target "#root"}
               (dom/text "Complete"))))
     (dom/button
      {:class "clear-completed"
       :hx-post "partial/clear"
       :hx-target "#root"}
      (dom/text "Clear completed")))))

(def sleep-sentinel nil)

(defn sleep
  [ms]
  (or sleep-sentinel
      (d/chain
       (d/future (Thread/sleep ms))
       (fn [_] (alter-var-root #'sleep-sentinel (constantly true))))))

(defn page
  [{:keys [filter]}]
  (dom/html5)
  (dom/head
   (dom/meta {:charset "UTF-8"})
   (dom/title "TodoMVC")
   (dom/link {:rel "stylesheet" :href "assets/todomvc-common/base.css"})
   (dom/link {:rel "stylesheet" :href "assets/todomvc-app-css/index.css"})
   (dom/script {:src "https://unpkg.com/htmx.org@1.8.5"})
   (dom/script {:src "assets/js/tld.js"}))
  (dom/body
   (dom/async
    (dom/div {:id "root"} (app {:filter filter :auto-focus? true}))
    (dom/use (sleep 1000))
    (fallback
     (dom/section
      {:class "todoapp"}
      (dom/header
       {:class "header"}
       (dom/h1 (dom/text "todos"))
       (dom/text "Loading...")))))
   (dom/script {:src "assets/todomvc-common/base.js"})
   ))

(defn page-handler
  [req]
  (alter-var-root #'sleep-sentinel (constantly nil))
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

(defn render-app
  [opts]
  (dom/render-string #(app opts)))

(defn ensure-vec
  [v]
  (if (vector? v) v [v]))

(defn partial-todos-handler
  [req]
  {:status 200
   :headers {"content-type" "text/html"}
   :body (render-app {:filter (-> req
                                  (get-in [:params "filter"] ["all"])
                                  (ensure-vec)
                                  (first)
                                  (keyword))})})

(defn partial-new-todo-handler
  [req]
  (let [{:strs [label filter]} (:params req)]
    (swap! db update :todos conj {:id (gensym "todo")
                                  :label label
                                  :completed false})
    {:status 200
     :headers {"content-type" "text/html"}
     :body (render-app {:filter (keyword filter)})}))

(defn partial-delete-todo-handler
  [req]
  (let [{:keys [id]} (:path-params req)
        {:strs [filter]} (:params req)]
    (swap! db update :todos (fn [todos]
                              (->> todos
                                   (remove #(= id (str (:id %))))
                                   (vec))))
    {:status 200
     :headers {"content-type" "text/html"}
     :body (render-app {:filter (keyword filter)})}))

(defn partial-update-todo-handler
  [req]
  (let [{:keys [id]} (:path-params req)
        {:strs [filter completed label]} (:params req)]
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
    {:status 200
     :headers {"content-type" "text/html"}
     :body (render-app {:filter (keyword filter)})}))

(defn partial-edit-todo
  [req]
  (let [{:keys [id]} (:path-params req)]
    {:status 200
     :headers {"content-type" "text/html"}
     :body (dom/render-string
            #(todo-item
              (-> @db
                  :todos
                  (->> (filter (fn [t] (= id (str (:id t))))))
                  (doto prn)
                  (first)
                  (assoc :editing? true))))}))

(defn partial-clear-handler
  [req]
  (swap! db update :todos (fn [todos]
                            (->> todos
                                 (filter #(not (:completed %)))
                                 (vec))))
  {:status 200
   :headers {"content-type" "text/html"}
   :body (render-app {:filter (keyword
                               (get-in req [:params "filter"]))})})

(defn partial-toggle-all-handler
  [req]
  (let [{:strs [toggle-all]} (:params req)]
    (swap! db update :todos
           (fn [todos]
             (->> todos
                  (map #(assoc % :completed (case toggle-all
                                              "on" true
                                              false)))
                  (vec))))
    {:status 200
     :headers {"content-type" "text/html"}
     :body (render-app {:filter (keyword
                            (get-in req [:params "filter"]))})}))

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
      ["/clear" {:post partial-clear-handler}]
      ["/toggle-all" {:post partial-toggle-all-handler}]]]
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

(declare server)

(when-not (instance? clojure.lang.Var$Unbound server)
  (prn "closing")
  (.close server)
  (netty/wait-for-close server))

(def server
  (http/start-server
   (ring/ring-handler router)
   {:port 9090
    :shutdown-timeout 0}))
