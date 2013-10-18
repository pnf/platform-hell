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

(defn daysecs-to-millis
  "Convert sequence of seconds since given datetime to millis since epoch"
  [date secs]
  (map #(dt/in-millis (dt/interval (dt/epoch)
                                   (dt/plus date
                                            (dt/seconds %))))
       secs))

(defn midnight-in-local [datestring]
  "Get ISO date, datetime and day of week at midnight of date given in string, which is
assumed to be in local zone."
  (let [tz       (dt/default-time-zone)
        fmt      (df/formatter "YYYY-MM-dd" tz)
        t        (if (seq datestring)
                   (df/parse fmt datestring)
                   (dt/to-time-zone (dt/now) tz))
        ds       (df/unparse fmt t)
        dt       (df/parse fmt ds)
        dw       (dt/day-of-week dt)]
    [ds dt dw]))

(defn nice-title [stop_id route_id]
  (let [stop_nm   (:stop_name  (mc/find-one-as-map "stops" {:stop_id stop_id}))
        dir      (if (= \S (last stop_id)) "Downtown" "Uptown")]
    (str dir " " route_id " at " stop_nm)))

(defn make-graph [date service_code stop_id route_id]
  (let [tz            (dt/default-time-zone)
        fmt           (df/formatter "YYYY-MM-dd" tz)
        [ds dt dw]    (midnight-in-local date)
        service_code  (cond (seq service_code) service_code
                            (= dw 6)          "SAT"
                            (= dw 7)          "SUN"
                            :else              "WKD")
        sched         (get-sched stop_id route_id service_code)
        rates         (get-rates stop_id route_id ds)]
    (doto
        (icharts/time-series-plot (daysecs-to-millis dt (map :now sched))
                                  (map :rate sched)
                       :title (nice-title stop_id route_id)
                       :x-label "Time" 
                       :y-label "Trains per hour"
                       :legend true
                       :series-label "Scheduled")
      (icharts/add-points (daysecs-to-millis dt (map :t_day rates))
                          (map :rate rates)
                          :series-label "Actual"))))

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
