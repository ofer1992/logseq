(ns frontend.extensions.git.core
  (:require #_["dugite" :refer [GitProcess]]
            ["isomorphic-git" :as git]
            #_[goog.object :as gobj]
            #_[electron.state :as state]
            [frontend.state :as state]
            #_[electron.utils :as utils]
            #_[electron.logger :as logger]
            [promesa.core :as p]
            [clojure.string :as string]
            [goog.string :as gstring]
            [goog.object :as gobj]
            [goog.string.format]
            [frontend.fs :as fs]
            [frontend.fs.protocol :as protocol]
            [frontend.fs.nfs :as nfs]
            #_["path" :as node-path]
            #_["os" :as os]

            ))
(comment
  (-> (fs/readdir "logseq_dev")
      (.then prn))
  (js-debugger))

(def graph-path "logseq_dev")
(def fs-interface {
    "readFile" fs/read-file
    "writeFile" fs/write-file!
    "rename" fs/rename!
    "mkdir" fs/mkdir!
    "readdir" fs/readdir
    "rmdir" fs/rmdir!
    "stat" (fn [path callback] (fs/stat path))
    "unlink" fs/unlink!
    "readlink" identity
    "lstat" identity
    "symlink" identity
})

(def fs-interface-debug {
    "readFile" (fn [& args] (prn args) (js-debugger) (apply fs/read-file args))
    "writeFile" (fn [& args] (prn args) (js-debugger) (apply fs/write-file! args))
    "rename" (fn [& args] (js-debugger) (apply fs/rename! args))
    "mkdir" (fn [& args] (js-debugger) (apply fs/mkdir! args))
    "readdir" (fn [& args] (js-debugger) (apply fs/readdir args))
    "rmdir" (fn [& args] (js-debugger) (apply fs/rmdir! args))
    "stat" (fn [path callback] (prn path callback) (js-debugger) (fs/stat path))
    "unlink" (fn [& args] (js-debugger) (apply fs/unlink! args))
    "readlink" (fn [& args] (js-debugger) (apply identity args))
    "lstat" (fn [& args] (js-debugger) (apply identity args))
    "symlink" (fn [& args] (js-debugger) (apply identity args))
}
)

;; (def log-error (partial logger/error "[Git]"))
(def log-error (partial js/console.log "[Git]"))

(defn get-graph-git-dir
  [graph-path]
  "TODO"
  #_(when-let [graph-path (some-> graph-path
                                (string/replace "/" "_")
                                (string/replace ":" "comma"))]
    (let [dir (.join node-path (.homedir os) ".logseq" "git" graph-path ".git")]
      (. fs ensureDirSync dir)
      dir)))

#_(defn run-git!
  [graph-path commands]
  (when (and graph-path (fs/existsSync graph-path))
    (p/let [result (.exec GitProcess commands graph-path)]
      (if (zero? (gobj/get result "exitCode"))
        (let [result (gobj/get result "stdout")]
          (p/resolved result))
        (let [error (gobj/get result "stderr")]
          (when-not (string/blank? error)
            (log-error error))
          (p/rejected error))))))

#_(defn run-git2!
  [graph-path commands]
  (when (and graph-path (fs/existsSync graph-path))
    (p/let [^js result (.exec GitProcess commands graph-path)]
      result)))

(defn git-dir-exists?
  [graph-path]
  "TODO"
  #_(try
    (let [p (.join node-path graph-path ".git")]
      (.isDirectory (fs/statSync p)))
    (catch :default _e
      nil)))

(defn remove-dot-git-file!
  [graph-path]
  "TODO"
  #_(try
    (let [_ (when (string/blank? graph-path)
              (utils/send-to-renderer :setCurrentGraph {})
              (throw (js/Error. "Empty graph path")))
          p (.join node-path graph-path ".git")]
      (when (and (fs/existsSync p)
                 (.isFile (fs/statSync p)))
        (let [content (string/trim (.toString (fs/readFileSync p)))
              dir-path (string/replace content "gitdir: " "")]
          (when (and content
                     (string/starts-with? content "gitdir:")
                     (string/includes? content ".logseq/")
                     (not (fs/existsSync dir-path)))
            (fs/unlinkSync p)))))
    (catch :default e
      (log-error e))))

(defn init!
  [graph-path]
  "TODO"
  #_(let [_ (remove-dot-git-file! graph-path)
        separate-git-dir (get-graph-git-dir graph-path)
        args (cond-> {:fs fs :dir graph-path}
               (git-dir-exists? graph-path) identity
               separate-git-dir (assoc :gitdir separate-git-dir))]
    (p/let [_ (git/init (clj->js args))]
      (when utils/win32?
        (git/setConfig (clj->js (assoc args :path "core.safecrlf" :value "false")))))))

(defn add-all!
  [graph-path]
  (p/let [fs (fs/get-fs graph-path)
          status (git/statusMatrix #js {:fs fs :dir graph-path :gitdir (get-graph-git-dir graph-path)})
          files (map first (js->clj status))
          add #(git/add #js {:fs fs :dir graph-path :gitdir (get-graph-git-dir graph-path) :filepath %})]
    ;; TODO filter files for modified ones
    (run! add files)))

(comment
  (keys (js->clj js/window.pfs))
  (p/let [fs (clj->js fs-interface)
          status (git/statusMatrix #js {:fs fs :dir graph-path #_#_:gitdir (get-graph-git-dir graph-path)})
          files (map first (js->clj status))
          #_#_add #(git/add #js {:fs fs :dir graph-path :gitdir (get-graph-git-dir graph-path) :filepath %})]
        ;; TODO filter files for modified ones
    (js/console.log files)
    (prn files)
    (js/console.log status)
    (js/console.log "hey"))
  (.replace (gobj/get #js {:fs (clj->js fs-interface) :dir graph-path :filepath "testing.lala"} "filepath") "la" "ba")
  (-> (fs/stat (str graph-path "/pages/testing.md"))
      (.then prn))
  (-> (fs/stat graph-path "/.git/info/exclude")
      (.then prn))
  (-> ((get fs-interface "stat") (str graph-path "/pages/testing.md") #(prn %1 %2))
      (.then prn))
  (-> (git/status #js {:fs (clj->js fs-interface) :dir graph-path :filepath "pages/testing.md"})
      (.then prn))
  (def filepath (str graph-path "/pages/testing.md"))
  (def filepath (str graph-path "/.git/info/exclude"))
  (keys @nfs/nfs-file-handles-cache)
  (-> (fs/stat (str graph-path filepath)) (.then prn))
  (def tnfs (fs/get-fs filepath))
  (-> (protocol/stat tnfs filepath) (.then prn))
  (p/let [
    handle (#'nfs/get-nfs-file-handle (str "handle/" filepath))
    f (.getFile handle)
    get-attr #(gobj/get f %)
  ]
    (js/console.log handle)
    (js/console.log f))
  
  (-> ((get fs-interface "stat") "logseq_dev/.git/info/exclude" identity) (.then prn))
)

(defn commit!
  [graph-path message]
  "TODO"
  #_(p/do!
   (git/setConfig #js {:fs fs :dir graph-path :gitdir (get-graph-git-dir graph-path) :path "core.quotepath" :value "false"}
   (git/commit #js {:fs fs :dir graph-path :gitdir (get-graph-git-dir graph-path) :message message})))
   )

#_(defn add-all-and-commit-single-graph!
  [graph-path message]
  (let [message (if (string/blank? message)
                  "Auto saved by Logseq"
                  message)]
    (->
     (p/let [_ (init! graph-path)
             _ (add-all! graph-path)]
       (commit! graph-path message))
     (p/catch (fn [error]
                (when (and
                       (string? error)
                       (not (string/blank? error)))
                  (if (string/starts-with? error "Author identity unknown")
                    (utils/send-to-renderer "setGitUsernameAndEmail" {:type "git"})
                    (utils/send-to-renderer "notification" {:type "error"
                                                            :payload (str error "\nIf you don't want to see those errors or don't need git, you can disable the \"Git auto commit\" feature on Settings > Version control.")}))))))))

(defn add-all-and-commit!
  ([]
   (add-all-and-commit! nil))
  ([message]
  "TODO"
   #_(doseq [path (state/get-all-graph-paths)] (add-all-and-commit-single-graph! path message))))

(defn short-status!
  [graph-path]
  "TODO"
  ;; good enough for now?
  #_(p/let [status (git/statusMatrix #js {:fs fs :dir graph-path :gitdir (get-graph-git-dir graph-path)})
          changed (filter #(apply not= (rest %)) (js->clj status))
          format-fn #(str (apply gstring/format "%d%d%d %s" (concat (rest %) [(first %)])))
          formatted (map format-fn changed)]
    (string/join "\n" formatted)))

(defonce quotes-regex #"\"[^\"]+\"")
(defn wrapped-by-quotes?
  [v]
  (and (string? v) (>= (count v) 2) (= "\"" (first v) (last v))))

(defn unquote-string
  [v]
  (string/trim (subs v 1 (dec (count v)))))

(defn- split-args
  [s]
  (let [quotes (re-seq quotes-regex s)
        non-quotes (string/split s quotes-regex)
        col (if (seq quotes)
              (concat (interleave non-quotes quotes)
                      (drop (count quotes) non-quotes))
              non-quotes)]
    (->> col
         (map (fn [s]
                (if (wrapped-by-quotes? s)
                  [(unquote-string s)]
                  (string/split s #"\s"))))
         (flatten)
         (remove string/blank?))))

#_(defn raw!
  [graph-path args]
  (init! graph-path)
  (let [args (if (string? args)
               (split-args args)
               args)
        error-handler (fn [error]
                        ;; TODO: why this happen?
                        (when-not (string/blank? error)
                          (let [error (str (first args) " error: " error)]
                            (utils/send-to-renderer "notification" {:type "error"
                                                                    :payload error}))
                          (p/rejected error)))]
    (->
     (p/let [_ (when (= (first args) "commit")
                 (add-all! graph-path))
             result (run-git! graph-path (clj->js args))]
       (p/resolved result))
     (p/catch error-handler))))

(defonce auto-commit-interval (atom nil))
(defn- auto-commit-tick-fn
  []
  (when ((not (state/disable-auto-commit?)))
    (add-all-and-commit!)))

(defn configure-auto-commit!
  "Configure auto commit interval, reentrantable"
  []
  (when @auto-commit-interval
    (swap! auto-commit-interval js/clearInterval))
  (when ((not (state/disable-auto-commit?)))
    (let [seconds (state/auto-commit-seconds)
          millis (if (int? seconds)
                   (* seconds 1000)
                   6000)]
      (js/console.log ::set-auto-commit-interval seconds)
      (js/setTimeout add-all-and-commit! 100)
      (reset! auto-commit-interval (js/setInterval auto-commit-tick-fn millis)))))

(defn before-graph-close-hook!
  []
  (when (and ((not (state/disable-auto-commit?))
             (state/commit-on-close?)))
    (add-all-and-commit!))
)