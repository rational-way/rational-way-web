(ns rationalway.web.disqus)

(defn disqus-thread [canonical-url page-identifier]
  [:div
   [:div {:id "disqus_thread"}]
   [:script (str "var disqus_config=function(){this.page.url=" canonical-url
                 ";this.page.identifier=" page-identifier ";};")]
   [:script {:src "script/disqus.js"}]
   [:noscript "Please enable JavaScript to view the comments."]
   ])