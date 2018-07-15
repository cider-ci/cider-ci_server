(ns ^:figwheel-no-load cider-ci.server.front.init
  (:require
    [cider-ci.server.front.main]
    [cider-ci.server.front.html]
    [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(figwheel/watch-and-reload
  :websocket-url "ws://localhost:3449/figwheel-ws"
  :jsload-callback cider-ci.server.front.html/mount)

(cider-ci.server.front.main/init!)
