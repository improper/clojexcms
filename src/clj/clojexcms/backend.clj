(ns clojexcms.backend
  (:require ; [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop)]
            [clojure.java.io :as io]
            [clojexcms.database :refer [db]]
            [clojexcms.dev :refer [is-dev? inject-devmode-html]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [taoensso.sente :as sente]
            [yesql.core :refer [defqueries]]))

(defqueries "backend/content.sql")

(deftemplate backend-page
  (io/resource "backend.html") []
  [:body] (if is-dev? inject-devmode-html identity))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! {})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids)) ; Watchable, read-only atom

(defmulti event-msg-handler :id) ; Dispatch on event-id

(defmethod event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (println "Unhandled event:" event)
    (when ?reply-fn
      (?reply-fn {:unmatched-event-as-echoed-from-from-server event}))))

(defmethod event-msg-handler :chsk/ws-ping [ev-msg]
  ;; do nothing
  )

(defmethod event-msg-handler :content/get-all
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (println ":content/get-all event:" event)
    (when ?reply-fn
      (?reply-fn (into {} (map (fn [ent] {(keyword (:id ent)) ent})
                               (content-all db)))))))

(defn start-chsk-router! []
  (sente/start-chsk-router! ch-chsk event-msg-handler))