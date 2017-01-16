(ns cider-ci.ui2.ui.navbar.release
  (:require
    [cider-ci.utils.core :refer [presence]]
    ))

(def emsp " ")
; watch out: this looks in coding-editors
; like a normal space but it is an unicode emspace

(defn navbar-release [release]
  [:b
   [:span " Cider-CI "]
   [:span.edition (or (-> @release :edition presence) "")] " "
   (when-let [name (-> @release :name presence)]
     [:span.name emsp [:em name] emsp])
   [:span.semantic-version
    [:span.major (-> @release :version_major)]
    [:span.divider "."]
    [:span.minor (-> @release :version_minor)]
    [:span.divider "."]
    [:span.patch (-> @release :version_patch)]
    [:span (when-let [pre (-> @release :version_pre presence)]
             [:span [:span.divider "-"] [:span.pre pre]])]]])
