(ns frontend.extensions.git.fs
  (:require
   [promesa.core :as p]
   ["isomorphic-git" :as git]
   [clojure.string :as string]
   [frontend.util :as util]
   [frontend.mobile.util :as mobile-util]
   [frontend.fs :as fs]
   [frontend.state :as state]
   [lambdaisland.glogi :as log]
   [goog.object :as gobj]
   [frontend.extensions.git.node :as node-backend]
   [frontend.extensions.git.capacitor :as capacitor-backend]
   [logseq.common.path :as path]))

;; required return value from stat
;; export interface Stats {
;;   type: 'file' | 'dir'
;;   mode: any
;;   size: number
;;   ino: any
;;   mtimeMs: any
;;   ctimeMs: any
;;   uid: 1
;;   gid: 1
;;   dev: 1
;;   isFile(): boolean
;;   isDirectory(): boolean
;;   isSymbolicLink(): boolean
;; }

(def graph-path "logseq_dev")

(defn split-base-dir [path]
  ;; TODO will probably need to handle windows paths
  ;; avoid using `util/node-path.join` to join mobile path since it replaces `file:///abc` to `file:/abc`
  (let [parts (string/split path #"/")]
    (if (empty? parts)
      ["/" ""]
      [(str (first parts)) (apply util/node-path.join (rest parts))])))

(comment
  (split-base-dir "logseq_dev/pages/testing.md")
  (util/node-path.join "hehe" "logseq_dev/pages/testing.md")
  (util/node-path.dirname "logseq_dev/pages/testing.md"))

(defn stat [path & options]
  (->
   (p/let [res (fs/stat path)]
     (log/info :stat {:path path :options options :res res})
     #_(js-debugger)
     res)
   (p/catch (fn [e]
              (let [e (js/Error. e) ]
                (gobj/set e "code" "ENOENT")
                (log/error :stat {:path path :options options :error e})
                (throw e))))))

(comment
  (stat "logseq_dev/pages/")
    (-> (fs/stat "logseq_dev/pages/testing3.md")
        (.catch js/console.error)
        )
)

(defn read-file
  ([] (p/promise nil))
  ([path] (read-file path {}))
  ([path options]
  ;;  (log/info :read-file { :path path :options options})
   (p/let [[dir path] (split-base-dir path)
           options (assoc (js->clj options :keywordize-keys true) :binary? false)
           options (if (contains? options :encoding)
                     options
                     (assoc options :binary? true))
           res (fs/read-file  dir path options)
           #_#_res (if (= path ".git/index") (js/Blob. (array res)) res)]
     (log/info :read-file {:path path :options options :res res})
     res
     )))
  
(comment 
;; (js/Blob. (array blob) (clj->js {:type "image"}))
  (def temp {:encoding "utf8"})
  
  (-> (read-file "logseq_dev/.git/index")
      (.then js/console.log))
  )

(defn write-file! [path content]
  (let [repo (state/get-current-repo)
        [dir rpath] (split-base-dir path)]
    (log/info :write-file! {:path path :content content})
    (fs/write-file! repo dir rpath content {})))

(comment
  (-> (fs/write-file! "logseq_local_logseq_dev" "logseq_dev" "pages/testing1.md" "haha" {})
      (.then js/console.log)
      (.catch js/console.error))
  (write-file! "logseq_dev/pages/testing2.md" "hahaha")
  [:repo "logseq_local_logseq_dev"]
  [:dir "logseq_dev"]
  [:rpath "journals/2024_05_13.md"]
  [:content-length 8])


(defn mkdir! [path]
  (log/info :mkdir! { :path path})
  (fs/mkdir! path))

(defn readdir [path]
  ;; todo nfs/readdir supports only the root dir. I suspect this will be a problem
  ;; (log/info :readdir { :path path})
  (p/let [res (fs/readdir path {:path-only? true})]
    (log/info :readdir {:path path :res res})
    res))

(defn rmdir! [path]
  (log/info :rmdir! { :path path})
  (fs/rmdir! path))

(comment
  (fs/readdir "logseq_dev/")
  (-> (readdir "logseq_dev/pages/")
      (.then #(log/info :readdir %))
      (.catch js/console.error)))

(def fs-interface {:promises
                   {:readFile read-file
                    :writeFile write-file!
                    ;; :rename fs/rename!
                    :mkdir mkdir!
                    :readdir readdir
                    :rmdir rmdir!
                    :stat stat
                    :lstat stat
                    :unlink fs/unlink!
                    :readlink identity
                    :symlink identity}})


(comment
  (log/info :testing "testing yo mama" :value "haha")
  (-> (stat "logseq_dev/pages/testing.md")
      (.then #(def -r %))
      (.catch js/console.error))
  -r
  ((:isDirectory -r))
  (-> (fs/read-file "logseq_dev" ".git/info/exclude") (.then js/console.log))
  (js/console.log cb)
  ((first cb) nil 1)


  (-> (git/status #js {:fs (clj->js fs-interface) :dir graph-path :filepath "pages/testing.md"})
      (.then js/console.log)
      (.then #(js/console.log "haha" %))
      (.finally (fn [r] (js/console.log "done" r))))

  (log/info :haha "haha"))

(defn- get-native-fs
  "Native FS backend of current platform"
  []
  (cond
    (util/electron?) node-backend/node-fs
    (mobile-util/native-platform?) capacitor-backend/capacitor-fs
    ;; TODO
    :else nil))

(comment
  (def base-dir "file:///storage/emulated/0/Documents/logseq_dev")
  (get-native-fs)
  (require '[goog.object :as gobj])
  (def fs (clj->js {:promises (get-native-fs)}))
  fs
  (-> ((:stat (get-native-fs)) (path/path-join base-dir ".git/info/exclude") {:bigint false})
      (p/then #(def -r %)))
  (-> ((:readFile (get-native-fs)) ".git/HEAD")
      (p/then #(def -r %))
      (p/catch js/console.error))
  -r
  (log/info :sanity "here")
  (type (gobj/get (clj->js -r) "isDirectory"))
  (js/console.log (clj->js -r))
  (-> (git/status #js {:fs fs :dir base-dir :filepath "pages/contents.md"})
      (p/then #(def -r %))
      (.then js/console.log)
      (.then #(js/console.log "haha" %))
      (.finally (fn [r] (js/console.log "done" r))))
  (-> (git/init #js {:fs fs :dir base-dir}))

  (-> (git/statusMatrix #js {:fs fs :dir "."})
      (.then js/console.log)
      (.catch js/console.error))
  (-> (git/log #js {:fs (clj->js fs-interface) :dir graph-path :depth 2 :ref "main"})
      (.then js/console.log))

  ((fn [x & {:keys [:a :b] :as xs}]
     (println x (:a xs)))
   1  1 2 :b 3))