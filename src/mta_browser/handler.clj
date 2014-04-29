(ns mta-browser.handler  
  (:require [compojure.core :refer [defroutes]]            
            [mta-browser.routes.home :refer [home-routes]]
            [mta-browser.hell-grapher :as hg]
            [noir.util.middleware :as middleware]
            [compojure.route :as route]
            [taoensso.timbre :as timbre]
            [com.postspectacular.rotor :as rotor]
            [clojure.java.io :as io]))

(defroutes app-routes
  (route/resources "/")
  (route/not-found "Not Found"))

(def properties (binding [*read-eval* false]
                              (with-open [r (io/reader "resources/properties.clj")]
              (read (java.io.PushbackReader. r)))))

(defn init
  "init will be called once when
   app is deployed as a servlet on
   an app server such as Tomcat
   put any initialization code here"
  []
  (timbre/set-config!
    [:appenders :rotor]
    {:min-level :info
     :enabled? true
     :async? false ; should be always false for rotor
     :max-message-per-msecs nil
     :fn rotor/append})

  (timbre/set-config!
    [:shared-appender-config :rotor]
    {:path "mta_browser.log" :max-size (* 512 1024) :backlog 10})

  (hg/init properties)

  (timbre/info "mta-browser started successfully")

)

(defn destroy
  "destroy will be called when your application
   shuts down, put any clean up code here"
  []
  (timbre/info "mta-browser is shutting down...")
  (hg/shutdown)
)

(def app (middleware/app-handler
           ;; add your application routes here
           [home-routes app-routes]
           ;; add custom middleware here
           :middleware []
           ;; add access rules here
           :access-rules []
           ;; serialize/deserialize the following data formats
           ;; available formats:
           ;; :json :json-kw :yaml :yaml-kw :edn :yaml-in-html
           :formats [:json-kw :edn]))

;(def war-handler (middleware/war-handler app))

