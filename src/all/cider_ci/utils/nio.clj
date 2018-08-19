; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.nio
  (:import
    [java.nio.file
     LinkOption
     Files
     FileSystem
     FileSystems
     FileVisitOption
     Path]
    [java.util
     Comparator]))

; ideas from
; https://github.com/potetm/nio2/blob/master/src/nio2/core.clj
; which unfortunately seems to be a bit broken, unfinished and abandoned

;### FS #######################################################################

(defn ^FileSystem default-fs [] (FileSystems/getDefault))

(def ^{:dynamic true :tag FileSystem} *fs* (default-fs))

(defn reset-fs [^FileSystem fs]
  (def ^:dynamic *fs* fs))



;### varargs ##################################################################

(defmacro varargs-array [type args]
  `(into-array ~type (or ~args [])))

(defmacro copy-opts [args] `(varargs-array CopyOption ~args))
(defmacro file-attrs [args] `(varargs-array FileAttribute ~args))
(defmacro link-opts [args] `(varargs-array LinkOption ~args))
(defmacro file-visit-opts [args] `(varargs-array FileVisitOption ~args))
(defmacro open-opts [args] `(varargs-array OpenOption ~args))


;### Protocol #################################################################

(defprotocol ^:private IPath
  (-path ^java.nio.file.Path [this paths]))

(extend-type FileSystem
  IPath
  (-path ^java.nio.file.Path [this [path & paths]]
    (let [^FileSystem this this]
      (.getPath this path (varargs-array String paths)))))

(extend-type String
  IPath
  (-path ^java.nio.file.Path [this paths]
    (.getPath *fs* this (varargs-array String paths))))

(extend-type Path
  IPath
  (-path ^java.nio.file.Path [this paths]
    (if (seq paths)
      (.getPath (.getFileSystem this)
                (str this)
                (varargs-array String (map str paths)))
      this)))


;### Path #####################################################################

(defn path ^java.nio.file.Path [& [fs-or-path-str & paths]]
  (-path fs-or-path-str paths))

(defn absolute ^java.nio.file.Path [^Path path]
  (.toAbsolutePath path))

(defn split [^Path path]
  (iterator-seq (.iterator path)))

(defn dir-stream
  "Lazily list the contents of a directory.
  Returns a new DirectoryStream. Should be used inside a with-open block.
  Because Streams implement Iterable, it can basically be used as a clojure seq."
  (^java.nio.file.DirectoryStream
    [^Path path]
    (Files/newDirectoryStream path))
  (^java.nio.file.DirectoryStream
    [^Path path ^String glob]
    (Files/newDirectoryStream path glob)))

(defn dir? ^Boolean [^Path path & link-options]
  (Files/isDirectory path (link-opts link-options)))

(defn rmdir-recursive [^Path path & file-visit-options]
  "dosen't work yet"
  (-> path
      (Files/walk (file-visit-opts file-visit-options))
      (.sorted (Comparator/reverseOrder))
      (.map #(.toFile %))
      (.forEach #(.delete %))))

