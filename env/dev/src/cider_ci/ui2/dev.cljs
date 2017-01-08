(ns ^:figwheel-no-load cider-ci.ui2.dev
  (:require [cider-ci.ui2.ui :as ui]
            [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(figwheel/watch-and-reload
  :websocket-url "ws://localhost:3449/figwheel-ws"
  :jsload-callback ui/mount)

(ui/init!)
