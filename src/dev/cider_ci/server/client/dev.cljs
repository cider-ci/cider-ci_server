(ns ^:figwheel-no-load cider-ci.server.client.dev
  (:require [cider-ci.server.client.main]
            [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(figwheel/watch-and-reload
  :websocket-url "ws://localhost:3449/figwheel-ws"
  :jsload-callback cider-ci.server.client.main/mount)

(cider-ci.server.client.main/init!)
