(defproject mta-browser "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [lib-noir "0.7.1"]
                 [compojure "1.1.5"]
                 [ring-server "0.3.0"]
                 [selmer "0.4.3"]
                 [com.taoensso/timbre "2.6.2"]
                 [com.postspectacular/rotor "0.1.0"]
                 [com.taoensso/tower "1.7.1"]
                 [markdown-clj "0.9.33"]
                 [incanter "1.5.4"]
                 [com.novemberain/monger "1.5.0"]
                 [clj-time "0.6.0"]]
  :plugins [[lein-ring "0.8.7"]]
  :ring {:handler mta-browser.handler/war-handler
         :init    mta-browser.handler/init
         :destroy mta-browser.handler/destroy}
  :profiles
  {:production {:ring {:open-browser? false
                       :stacktraces?  false
                       :auto-reload?  false}}
   :dev {:dependencies [[ring-mock "0.1.5"]
                        [ring/ring-devel "1.2.0"]]}}
  :min-lein-version "2.0.0")
