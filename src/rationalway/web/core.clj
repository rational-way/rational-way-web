(ns rationalway.web.core
  (:require [hiccup.page :as pg]
            [hiccup.element :as el]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [markdown.core :refer [md-to-html-string md-to-html-string-with-meta]]
            [environ.core :refer [env]]
            [rationalway.web.disqus :refer [disqus-thread]]))

(def local-url (str (System/getProperty "user.dir") "/web/"))
(def canonical-url (:canonical-url env local-url))
(def date-format (tf/formatters :date))
(def file-name-cursor [:metadata :file-name])
(def date-cursor [:metadata :date])
(def desc-cursor [:metadata :desc])
(def tags-cursor [:metadata :tags])
(def prev-cursor [:metadata :prev])
(def next-cursor [:metadata :next])

(defn absolute-url [relative]
  (str canonical-url relative))

(defn link [relative-target & content]
  [:a {:href (absolute-url relative-target)} content])

(def heading
  [:div (link "index.html" [:h1 "rational-way"])])

(def about-me
  [:div
   [:h3 "About me"]
   (el/image (absolute-url "img/profile.png") "tomas-zaoral")
   [:p "Clojurian, player, dancer."]])

(def highlight-js-headers
  [[:link {:rel "stylesheet" :href (absolute-url "css/darkula.css")}]
   [:script {:src (absolute-url "script/highlight.pack.js")}]
   [:script "hljs.initHighlightingOnLoad();"]])

(defn tag-page-name [tag]
  (str "tag-" tag "-page.html"))

(defn tag-page-header [tag count]
  [:div "Showing " count (if (= count 1) " post" " posts") " marked with tag " [:b tag] "."])

(defn post-summary [{:keys [metadata]}]
  [:div
   [:div (link (:file-name metadata) (:name metadata))]
   [:div (str (:author metadata) ", " (tf/unparse date-format (:date metadata)))]
   [:div (link (:file-name metadata) (:desc metadata))]
   [:div (->> (:tags metadata) (map (fn [tag] (link (tag-page-name tag) tag))) (interpose ", "))]])

(defn prev-next-links [metadata]
  [:div (->> [(when-some [prev (:prev metadata)] (link prev "previous"))
              (when-some [next (:next metadata)] (link next "next"))]
             (filter some?)
             (interpose " "))])

(defn add-prev-next-links [posts]
  (let [file-names (map #(get-in % file-name-cursor) posts)
        next-fnames (conj (drop-last file-names) nil)
        prev-fnames (conj (vec (rest file-names)) nil)
        posts-prev-next (map vector posts next-fnames prev-fnames)]
    (map (fn [[post next prev]] (assoc-in (assoc-in post next-cursor next) prev-cursor prev)) posts-prev-next)))

(def posts
  (->> (file-seq (io/file "posts"))
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
  [:div "tags: " (->> tags (mapcat second) (map #(link (tag-page-name %) %)) (interpose " "))])

(defn page
  ([content] (page [] content))
  ([headers content]
   (pg/html5 {:lang "en"}
             (into [:head] headers)
             [:body heading about-me tags-bar content])))

(defn -main []

  ;post pages
  (doseq [{:keys [metadata html summary-html]} posts]
    (spit
      (str "web/" (:file-name metadata))
      (page
        highlight-js-headers
        [:div (prev-next-links metadata) summary-html html
         (disqus-thread canonical-url (:file-name metadata))])))

  ;tag pages
  (doseq [[count ts] tags tag ts]
    (spit
      (str "web/" (tag-page-name tag))
      (page [:div (tag-page-header tag count)
             [:div (->> posts
                        (filter (fn [post] (some #{tag} (get-in post tags-cursor))))
                        (map :summary-html))]])))

  ;index page
  (spit "web/index.html" (page [:div (map :summary-html posts)])))

(comment
  (-main)
  )
