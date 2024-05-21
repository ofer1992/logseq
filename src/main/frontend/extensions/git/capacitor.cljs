(ns frontend.extensions.git.capacitor
  (:require ["@capacitor/filesystem" :refer [Encoding Filesystem Directory]]
            ["isomorphic-git" :as git] ;; TODO temp
            [frontend.config :as config]
            [frontend.state :as state]
            [promesa.core :as p]
            [lambdaisland.glogi :as log]
            [goog.object :as gobj]
            #_[goog.crypt.base64 :as b64]
            [goog.crypt.base64]
            [logseq.common.path :as path]
            [goog.crypt.base64 :as base64]
            ["fs-extra" :as fs]
            [clojure.string :as string]))

(def base-dir (config/get-repo-dir (state/get-current-repo)))

(defn- create-stat [res]
  (let [new {:ino 1
             :uid 1
             :gid 1
             :dev 1
             :mtimeMs (:mtime res)
             :ctimeMs (or (:ctime res) (:mtime res))
             :mode (if (= "file" (:type res)) 438 511)
             :isFile (fn [] (= "file" (:type res)))
             :isDirectory (fn [] (= "directory" (:type res)))
             :isSymbolicLink (fn [] (= "symlink" (:type res)))}]
    (merge res new)))

(defn fs-error [e code]
  (let [e (js/Error. e)]
    (gobj/set e "code" code)
                ;; (log/error :stat {:path path :options options :error e})
    e
    #_(throw e)))

(defn- base64->uint8 [data]
  ;; TODO might need to return an array buffer
  (let [data (.replace data #"[^A-Za-z0-9+/]" "")
        buffer (goog.crypt.base64.decodeStringToByteArray data)
        buffer (js/Uint8Array. buffer)]
    buffer))

(defn- uint8->base64 [data]
  (goog.crypt.base64.encodeByteArray  (js/Uint8Array. data)))

(comment
  (set! js/Test (base64-uint8 " 12"))
  (uint8->base64 [0 0 0])
  (-> (:readFile fs) "file:/storage/emulated/0/Documents/logseq_dev/.git/HEAD" {:encoding "utf8"}
      (p/then #(def -r %))))

(def capacitor-fs
  (as-> {:mkdir (fn mkdir! [dir]
                  (.mkdir Filesystem
                          (clj->js
                           {:path dir})))

         :readdir (fn readdir [dir]                  ; recursive
                    (p/let [result (.readdir Filesystem (clj->js {:path dir}))
                            result (js->clj result :keywordize-keys true)
                            files (map :name (:files result))]
                      (log/info :readdir {:dir dir :files files})
                      files))
         :unlink identity #_(fn unlink! [this repo fpath _opts]
                              (p/let [repo-dir (config/get-local-dir repo)
                                      recycle-dir (path/path-join repo-dir config/app-name ".recycle") ;; logseq/.recycle
            ;; convert url to pure path
                                      file-name (-> (path/trim-dir-prefix repo-dir fpath)
                                                    (string/replace "/" "_"))
                                      new-path (path/path-join recycle-dir file-name)
                                      _ (protocol/mkdir-recur! this recycle-dir)]
                                (protocol/rename! this repo fpath new-path)))

         :rmdir (fn rmdir! [_dir]
    ;; Too dangerous!!! We'll never implement this.
                  nil)

         :readFile (fn read-file [path options]
         ;; todo stat check that it's a file
         ;; what to do when called from js
                     (log/info :readFile {:path path :options options})
                     (if (nil? path)
                       (p/promise nil)
                       (p/let [options (js->clj options :keywordize-keys true)
                               res (.readFile Filesystem (clj->js (merge {:path path} options)))
                               data (.-data res)
                               data (if (nil? (get options :encoding)) (base64->uint8 data) data)]
                         (log/info :readFile {:path path :options options :data data})
                         data)))

         :writeFile (fn write-file! [path content opts]
                      (log/info :writeFile {:path path :content content :opts opts})
                      (when-not (string/blank? path)
                        (p/let [utf (:encoding (js->clj opts :keywordize-keys true))
                                content (if (= utf "utf8") content (uint8->base64 content))]
                          (.writeFile Filesystem (clj->js {:path path :data content :encoding (get opts :encoding)})))))

         :stat (fn stat [path & options]
                 (log/info :stat {:path path :options options})
                 (-> (p/let [res (.stat Filesystem (clj->js {:path path}))
                             updated (clj->js (create-stat (js->clj res :keywordize-keys true)))]
                       (log/info :stat {:path path :options options :res res :updated updated})
                       updated)
                     (p/catch #(fs-error % "ENOENT"))
                     #_(p/catch (fn [e] (let [e (fs-error e "ENOENT")] (set! js/test e) (throw e))))))
                  ;;  :lstat (fn lstat [path]
                            ;; (.stat Filesystem (clj->js {:path path})))

         :readlink identity

         :symlink identity} fs
    (assoc fs :lstat (:stat fs))))


(comment
  (def fs capacitor-fs)
  (-> (.readdir Filesystem #js {:path base-dir})
      (p/then #(def -r %)))
  (set! js/test cap-fs)
  (set! js/baseDir base-dir)
  (:readlink fs)
  (println cap-fs)
  (js->clj cap-fs)
  (do
    (-> ((:stat fs) (path/path-join base-dir "pages/contents.md"))
        (p/then #(def -r (js->clj %)))
        (p/then #(js/console.log %))
        (p/catch #(js/console.error %))))
  (js->clj -r)
  (-> (.readFile Filesystem (clj->js {:path (path/path-join base-dir "pages/contents.md") :encoding "utf8"}))
      (p/then #(def -r %)))
  (-> ((:readFile fs) (path/path-join base-dir ".git/HEAD"))
      (p/then #(def -r %))
      (p/then #(js/console.log %))
      (p/catch #(js/console.error %)))
  (def tmpdir "file:/storage/emulated/0/Documents/Logseq/pages/contents.md")
  (-> (p/let [res (.stat Filesystem #js {:path (path/path-join base-dir ".git/info/exclude")})]
        (js/console.log res))
      (p/catch #(js/console.log "caught" %)))
  (-> (p/let [res (.stat Filesystem #js {:path tmpdir})]
        (js/console.log res))
      (p/catch #(js/console.log "caught" %)))
  (->
   (p/catch (fn [e] (js/console.log "caught" e))))
  (js/console.log -r)
  (set! js/test (clj->js -r))
  (goog.crypt.base64)
  (goog.crypt.base64.decodeStringToByteArray "12")
  (-> ((:readFile fs) "file:/storage/emulated/0/Documents/logseq_dev/.git/HEAD" {:encoding "utf8"})
      (p/then #(def -r %)))
  -r
  (-> ((:readdir fs) "file:/storage/emulated/0/Documents/logseq_dev/journals")
      (p/then #(def -r %)))
  -r
  (def tmppath  "file:/storage/emulated/0/Documents/logseq_dev/testing_yo.md")
  (.writeFile Filesystem (clj->js {:path tmppath :data "hahaha" :encoding "utf8"}))
  ((:writeFile fs) tmppath "hehehe" {:encoding "utf8"}))

