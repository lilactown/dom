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
         (d/patch #(d/$ "input" {:class "foo"}))))
  (is (= "<div selected=\"\" muted=\"\" contenteditable=\"true\" multiple=\"\" style=\"color:red\" http-equiv=\"content-security-policy\" for=\"baz\" draggable=\"true\" id=\"foo\" accept-charset=\"utf8\" class=\"foo bar\" spellcheck=\"false\"></div>"
         (d/patch #(d/$ "div"
                        {:style {:color "red"}
                         :id "foo"
                         :class ["foo" "bar"]
                         :for "baz"
                         :accept-charset "utf8"
                         :http-equiv "content-security-policy"
                         :default-value 7
                         :default-checked true
                         :multiple true
                         :muted true
                         :selected true
                         :children '("foo" "bar" "baz")
                         :inner-html "<span></span>"
                         :suppress-content-editable-warning true
                         :suppress-hydration-warning true
                         :content-editable true
                         :spell-check false
                         :draggable true})))
      "kitchen sink")
  (is (= "<div style=\"color:red\"></div>"
         (d/patch #(d/$ "div"
                        {& {:style {:color "red"}}})))))

(deftest html-escape
  (is (= "<div>&lt;button class=&quot;foo&quot;&gt;hi&lt;/button&gt;</div>"
         (d/patch #(d/$ "div" (d/text "<button class=\"foo\">hi</button>"))))))
