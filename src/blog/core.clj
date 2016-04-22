(ns blog.core
  (:require [hiccup.page :as pg]
            [hiccup.element :as el]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:use [markdown.core :only [md-to-html-string md-to-html-string-with-meta]]))

(def date-format (tf/formatters :date))
(def file-name-cursor [:metadata :file-name])
(def date-cursor [:metadata :date])
(def desc-cursor [:metadata :desc])
(def tags-cursor [:metadata :tags])
(def prev-cursor [:metadata :prev])
(def next-cursor [:metadata :next])

(def header
  [:div (link "index.html" [:h1 "my-blog"])])

(def about-me
  [:div
   [:h3 "About me"]
   (el/image "img/profile.png" "tomas-zaoral")
   [:p "Clojurian, player, dancer."]])

(defn link [target & content]
  [:a {:href target} content])

(defn tag-page-name [tag]
  (str "tag-" tag "-page.html"))

(defn tag-page-header [tag count]
  [:div "Showing " count (if (= count 1) " post" " posts") " marked with tag " [:b tag] "."])

(defn post-summary [{:keys [metadata]}]
  [:div
   [:div (link (:file-name metadata) (:name metadata))]
   [:div (str (:author metadata) ", " (tf/unparse date-format (:date metadata)))]
   [:div (link (:file-name metadata) (:desc metadata))]
   (into [:div]
     (->> (:tags metadata)
       (map (fn [tag] (link (tag-page-name tag) tag)))
       (interpose ", ")))])

(defn prev-next-links [metadata]
  (into [:div] (comp (interpose " ") (filter some?))
    [(when-some [prev (:prev metadata)] (link prev "previous"))
     (when-some [next (:next metadata)] (link next "next"))]))

(defn add-prev-next-links [posts]
  (let [file-names (map #(get-in % file-name-cursor) posts)
        prev-fnames (conj (drop-last file-names) nil)
        next-fnames (conj (vec (rest file-names)) nil)
        posts-prev-next (map vector posts prev-fnames next-fnames)]
    (map (fn [[post prev next]] (assoc-in (assoc-in post prev-cursor prev) next-cursor next)) posts-prev-next)))

(def posts
  (->> (file-seq (io/file "resources/posts"))
    (filter #(.. % (getName) (endsWith ".mmd")))
    (map (fn [file] [(apply str (take-while #(not= \. %) (.getName file))) (slurp file)]))
    (map (fn [[fname file]] (assoc-in (md-to-html-string-with-meta file) file-name-cursor [(str fname ".html")])))
    (map (fn [{:keys [metadata] :as post}] (assoc post :metadata (zipmap (keys metadata) (map first (vals metadata))))))
    (map (fn [post] (assoc-in post tags-cursor (str/split (get-in post tags-cursor) #","))))
    (map (fn [post] (assoc-in post desc-cursor (md-to-html-string (get-in post desc-cursor)))))
    (map (fn [post] (assoc-in post date-cursor (tf/parse date-format (get-in post date-cursor)))))
    (map (fn [post] (assoc post :summary-html (post-summary post))))
    (sort-by #(get-in % date-cursor) t/after?)
    (add-prev-next-links)))

(def tags
  (->> posts
    (mapcat #(get-in % tags-cursor))
    (group-by identity)
    (map (fn [[tag occ]] [tag (count occ)]))
    (group-by second)
    (mapcat (fn [[occ vals]] [occ (set (map first vals))]))
    (apply (partial sorted-map-by >))))

(def tags-bar
  (into [:div "tags: "] (comp (mapcat second) (map #(link (tag-page-name %) %)) (interpose " ")) tags))


(defn page [contents]
  (pg/html5 {:lang "en"} [:head] [:body header about-me tags-bar contents]))

;post pages
(doseq [{:keys [metadata html summary-html]} posts]
  (spit
    (str "web/" (:file-name metadata))
    (page [:div (prev-next-links metadata) summary-html html])))

;tag pages
(doseq [[count ts] tags tag ts]
  (spit
    (str "web/" (tag-page-name tag))
    (page [:div (tag-page-header tag count)
           [:div (->> posts
                   (filter (fn [post] (some #{tag} (get-in post tags-cursor))))
                   (map :summary-html))]])))

(spit "web/index.html" (page [:div (map :summary-html posts)]))
