(ns mta-browser.routes.home
  (:use compojure.core)
  (:require [mta-browser.views.layout :as layout]
            [mta-browser.util :as util]
             [monger.core :as mg]
             [monger.collection :as mc]
             [monger.operators :as mo]
            [incanter.core :as icore]
            [incanter.charts :as icharts]
            [clj-time.core :as dt]
            [clj-time.format :as df]
            [noir.response :as nr]))

(defn home-page []
  (layout/render
    "home.html" {:content (util/md->html "/md/docs.md")}))

(defn- get-sched [stop_id route_id service_code]
  (mc/find-maps "sched" {:route_id       route_id
                         :stop_id        stop_id
                         :service_code   service_code}))

(defn- get-rates [stop_id route_id day]
  (mc/find-maps "rates" {:route_id   route_id
                         :day        day
                         :stop_id    stop_id}))


(defn make-graph [date service_code stop_id route_id]
  (let [t             (if-not (empty? date) (df/parse date) (dt/now))
        date          (df/unparse (df/formatter "YYYY-MM-dd") t)
        service_code  (cond (seq service_code)         service_code
                            (= (dt/day-of-week t) 6)  "SAT"
                            (= (dt/day-of-week t) 7)  "SUN"
                            :else                     "WKD")
        sched         (get-sched stop_id route_id service_code)
        rates         (get-rates stop_id route_id date)]
    (doto
      (icharts/xy-plot (map :now sched) (map :rate sched))
      (icharts/add-points (map :t_day rates) (map :rate rates)))))

(defn show-graph [date service_code stop_id route_id]
  (cond
   (or (empty? stop_id)
       (empty? route_id))   "Format graph?date=YYYY-MM-DD&stop_id=NNND&route_id=R"
    :else
    (let [graph     (make-graph date service_code stop_id route_id)
          os        (java.io.ByteArrayOutputStream.)
          is        (do (icore/save graph os)
                        (java.io.ByteArrayInputStream. 
                         (.toByteArray os)))]
      (nr/content-type "image/png" is))))

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/graph"
       [date service_code stop_id route_id]
       (show-graph date service_code stop_id route_id)))
