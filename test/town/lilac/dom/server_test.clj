(ns town.lilac.dom.server-test
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [town.lilac.dom.server :as d]))


(deftest void
  (is (= "<input>" (d/patch #(d/$ "input")))))

(deftest non-void
  (is (= "<div><button>hi</button></div>"
         (d/patch #(d/$ "div"
                        (d/$ "button"
                             (d/text "hi")))))))

(deftest attributes
  (is (= "<input class=\"foo\">"
         (d/patch #(d/$ "input" {:class "foo"})))))
