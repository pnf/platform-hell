(defproject mta-browser "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [lib-noir "0.8.2"]
                 [compojure "1.1.6"]
                 [ring-server "0.3.1"]
                 [selmer "0.6.6"] ; django-like templating
                 [com.taoensso/timbre "2.6.2"] ;logging and profiling
                 [com.postspectacular/rotor "0.1.0"] ; log rotator
                 ;[com.taoensso/tower "1.7.1"]
                 [markdown-clj "0.9.43"]
                 [incanter "1.5.5"]
                 [com.novemberain/monger "1.7.0"]
                 [clj-time "0.7.0"]]
  :plugins [[lein-ring "0.8.10"]]
  :ring {:handler mta-browser.handler/war-handler
         :init    mta-browser.handler/init
         :destroy mta-browser.handler/destroy}
  :profiles
  {:production {:ring {:open-browser? false
                       :stacktraces?  false
                       :auto-reload?  false}}
   :dev {:dependencies [[ring-mock "0.1.5"]
                        [ring/ring-devel "1.3.0-beta1"]]}}
  :min-lein-version "2.0.0")
