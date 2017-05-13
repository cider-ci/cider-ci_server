(ns ^:figwheel-no-load cider-ci.ui2.dev
  (:require [cider-ci.client.main]
            [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(figwheel/watch-and-reload
  :websocket-url "ws://localhost:3449/figwheel-ws"
  :jsload-callback cider-ci.client.main/mount)

(cider-ci.client.main/init!)
