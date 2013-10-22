(ns mta-browser.routes.home
  (:use compojure.core monger.operators )
  (:require [mta-browser.views.layout :as layout]
            [mta-browser.util :as util]
            [mta-browser.hell-grapher :as hg]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :as mo]
            [incanter.core :as icore]
            [incanter.charts :as icharts]
            [clj-time.core :as dt]
            [clj-time.format :as df]
            [noir.response :as nr]))


(defn show-graph [date service_code platform]
  (let [platform ((:code-> (hg/get-platforms)) platform)]
    (if-not platform
      "Format graph?date=YYYY-MM-DD&platform=NNND:R"
      (let [gstream     (hg/make-graph-stream date service_code
                                  (:stop_id platform)
                                  (:route_id platform))]
        (nr/content-type "image/png" gstream)))))

(defn home-page []
  (layout/render
   "home.html" {:platforms (:sorted (hg/get-platforms))}))

(defroutes home-routes
  (GET "/hell" [] (home-page))
  (GET "/hell/images/graph"
       [date service_code platform]
       (show-graph date service_code platform)))
