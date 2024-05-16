(ns frontend.extensions.git.node
  (:require [cljs-bean.core :as bean]
            [clojure.string :as string]
            [electron.ipc :as ipc]
            [promesa.core :as p]
            [frontend.config :as config]
            [frontend.state :as state]
            [lambdaisland.glogi :as log]
            [logseq.common.path :as path]))

(def repo (config/get-repo-dir (state/get-current-repo)))

(def node-fs
  (as-> {:mkdir (fn mkdir! [dir]
                  (-> (ipc/ipc "mkdir" dir)
                      (p/then (fn [_] (js/console.log (str "Directory created: " dir))))
                      (p/catch (fn [error]
                                 (when-not (string/includes? (str error) "EEXIST")
                                   (js/console.error (str "Error creating directory: " dir) error))))))

         :rmdir (fn rmdir! [_dir]
    ;; !Too dangerous! We'll never implement this.
                  nil)


         :readdir (fn readdir [dir]                   ; recursive
                    (p/then (ipc/ipc "readdirGit" dir)
                            bean/->clj))

         :unlink (fn unlink! [path _opts]
                   (ipc/ipc "unlink"
                            repo
                            path))

         :readlink identity

         :symlink identity


         :readFile (fn read-file [path options]
                     (p/let [fpath (path/path-join repo path)
                           result (ipc/ipc "readFileGit" fpath options)]
                       (log/info :readFile {:fpath fpath :options options :result result})
                       (if (nil? options)
                         (.-buffer result) 
                         result)
                       ))

         :writeFile (fn write-file! [fpath content]
                      (ipc/ipc "writeFile" repo fpath content))

         :stat (fn stat [fpath options]
                 (let [fpath (path/path-join repo fpath)]
                   (log/info :stat {:fpath fpath :options options})
                   (-> (ipc/ipc "stat" fpath options)
                       (p/then bean/->clj)
                       (p/then #(assoc % :isDirectory (fn [& args] (:isDirectory %))))
                       (p/then #(assoc % :isFile (fn [& args] (:isFile %))))
                       (p/then #(assoc % :isSymbolicLink (fn [& args] (:isSymbolicLink %))))
                       (p/then bean/->js))))} node-fs

    (assoc node-fs :lstat (:stat node-fs))))