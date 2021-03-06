(ns mta-browser.hell-grapher
  (:use compojure.core monger.operators )
  (:require  [taoensso.timbre :as timbre]
             [monger.core :as mg]
             [monger.collection :as mc]
             [monger.operators :as mo]
             [monger.query :as mq]
             [incanter.core :as icore]
             [incanter.charts :as icharts]
             [clj-time.core :as dt]
             [clj-time.format :as df]))


(defn init [properties]
  (timbre/info "Connecting to mongo on port" (:mongo-port properties))
  (mg/connect! {:port (:mongo-port properties)})
  (mg/set-db! (mg/get-db "mta")))

(defn shutdown [] (mg/disconnect!))

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


(defn throttled-fetch [cache millis fetcher & args]
  (let [now      (System/currentTimeMillis)
        then     (- now millis)]
    (-> cache
        (swap! (fn [{time :time :as orig}]
                 (if (> time then)
                   orig
                   {:time now :data (apply fetcher args)})))
        :data)))

(def stop-name-cache (atom {:time 0}))
(defn- fetch-stop-names []
  (reduce #(assoc %1 (:stop_id %2) (:stop_name %2)) {}                           
          (mc/find-maps "stops")))
(defn get-stop-names [] 
  (throttled-fetch stop-name-cache (* 7 24 3600 1000) fetch-stop-names))

(defn nice-title [stop_id route_id]
  (let [stop_nm   ((get-stop-names) stop_id)
        dir       (if (= \S (last stop_id)) "Downtown" "Uptown")]
    (str stop_nm ": " dir " " route_id)))

(def platform-compare (comparator (fn [rec1 rec2]
                                    (neg? (compare (:title rec1)
                                                   (:title rec2))))))
(defn- fetch-platforms
  "Returns list of maps like
 {:station_name \"33 St\", :route_id \"5\", :dir \"N\", :stop \"632\", :stop_id \"632N\"}"
  []
  (let [one-week-ago    (-  (int (/ (System/currentTimeMillis) 1000)) (* 7 24 3600))
        query           [{$match {:now {$gt one-week-ago}}}
                         {$project {:platform {"$concat" ["$stop_id" ":" "$route_id"]}}}
                         {$group {:_id "na" :platforms {$addToSet "$platform"}}}]
        code->platform  (->> (mc/aggregate "rates" query) first :platforms
                      ; ==> "132S:2", "632N:5", etc.
                             (map (partial re-find #"((\S+)([NS])):(\S+)"))
                             (map #(zipmap [:code :stop_id :station :dir :route_id] %))
                             (map #(assoc %
                                     :title (nice-title (:stop_id %) (:route_id %))
                                     :station_name ((get-stop-names) (:stop_id %))))
                             (reduce #(assoc %1 (:code %2) %2) {}))
        sorted-platforms (sort platform-compare (vals code->platform))]
    {:code-> code->platform :sorted sorted-platforms}))

;; nb. using sched table would include routes for which we don't actually have data
#_(defn- fetch-platforms
  "Returns list of maps like
 {:station_name \"33 St\", :route_id \"5\", :dir \"N\", :stop \"632\", :stop_id \"632N\"}"
  []
  (let [query           [{$project {:platform {"$concat" ["$stop_id" ":" "$route_id"]}}}
                         {$group {:_id "na" :platforms {$addToSet "$platform"}}}]
        code->platform  (->> (mc/aggregate "sched" query) first :platforms
                      ; ==> "132S:2", "632N:5", etc.
                             (map (partial re-find #"((\S+)([NS])):(\S+)"))
                             (map #(zipmap [:code :stop_id :station :dir :route_id] %))
                             (map #(assoc %
                                     :title (nice-title (:stop_id %) (:route_id %))
                                     :station_name ((get-stop-names) (:stop_id %))))
                             (reduce #(assoc %1 (:code %2) %2) {}))
        sorted-platforms (sort platform-compare (vals code->platform))]
    {:code-> code->platform :sorted sorted-platforms}))

(def platform-cache (atom {:time 0}))
(defn get-platforms []
  (throttled-fetch platform-cache (* 7 24 3600 1000) fetch-platforms ))

; Want latest "now" for vehicle trip at station
(defn- get-etas-at-station [stop_id]
  (let [now     (:now (first  (mq/with-collection "etas"
                                (mq/find {:stop_id stop_id})
                                (mq/sort {:now -1})
                                (mq/limit 1))))
        trips    (set (map :trip_id  (mc/find-maps "etas" {:now {mo/$gte now}
                                                        :stop_id stop_id})))
        etas     (mq/with-collection "etas"
                   (mq/find {:now {mo/$gte now mo/$lte (+ now 900)}
                             :trip_id {mo/$in trips}})
                   (mq/limit 500)
                   (mq/fields [:trip_id :route_id :stop_id]))
        ]
    [trips etas]
    ))

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

(defn- chart->stream [chart]
  (let [os (java.io.ByteArrayOutputStream.)]
    (do (icore/save chart os)
        (java.io.ByteArrayInputStream. (.toByteArray os)))))

(defn make-graph-stream [date service_code stop_id route_id]
  (chart->stream (make-graph date service_code stop_id route_id)))

