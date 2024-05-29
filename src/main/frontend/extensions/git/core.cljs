(ns frontend.extensions.git.core
  (:require #_["dugite" :refer [GitProcess]]
            ["isomorphic-git" :as git]
            ;; import http from "isomorphic-git/http/web";
            ["isomorphic-git/http/web" :as http2]
            #_[cljs-http.client :as http]
            ["@capacitor/core" :refer [CapacitorHttp]]
            [frontend.config :as config]
            [frontend.state :as state]
            [promesa.core :as p]
            [clojure.string :as string]
            [goog.string :as gstring]
            [goog.object :as gobj]
            [goog.crypt.base64 :as base64]
            [frontend.handler.notification :as notification]
            ;; [goog.string.format]
            [lambdaisland.glogi :as log]
            [frontend.extensions.git.capacitor :as capacitor-backend]))


#_(def base-dir (config/get-repo-dir (state/get-current-repo)))
;; (def fs (clj->js {:promises (capacitor-backend/get-fs)}))
(defonce fs (clj->js {:promises (capacitor-backend/get-fs)}))
(def pat "")
(defn- uint8->base64 [data]
  (goog.crypt.base64.encodeByteArray  (js/Uint8Array. data)))
(defn request [{:keys [url method headers body] :as opts}]
  (def -r body)
  (js/console.log opts url method headers body)
  (p/let [#_#_body (when body (map uint8->base64 body))
          #_#_headers (when headers (assoc headers :dataType "base64"))
          body (clj->js [(js/Uint8Array.from (apply concat (map vec body)))])
          res (.request CapacitorHttp (clj->js {:method method :url url :headers headers :body body}))
          res (js->clj res :keywordize-keys true)
          enc (js/TextEncoder. "utf-8")
          {:keys [data, status, headers-out, HttpHeaders, url-out] :as out-cap} res
          url (if url-out url-out url)
          headers (if headers-out headers-out headers)
          out {:url url :method method :headers headers :body [(.encode enc data)] :statusCode status :statusMessage status}]
    (js/console.log out-cap)
    (js/console.log out)
    out)
  #_(http/request {:method method :url url :headers headers :body body :onProgress onProgress :signal signal}))

(def http (clj->js {:request (fn [opts] (p/let [res  (request (js->clj opts :keywordize-keys true))] (clj->js res)))}))

(comment
  (js/Uint8Array.from (concat [1 2] (vec (js/Uint8Array. [1 2 3 4]))))
  (js/console.log #js [1 2 3])
  (js/Array.from [1 2 3])
;; const http = {
;;   async request ({
;;     url,
;;     method,
;;     headers,
;;     body,
;;     onProgress
;;   }) {
;;     ...
;;     // Do stuff
;;     ...
;;     return {
;;       url,
;;       method,
;;       headers,
;;       body,
;;       statusCode,
;;       statusMessage
;;     }
;;   }
;; }
;; js api for http
  (js/console.log http)
  ;; let's try a call
  (js/console.log CapacitorHttp)
  (-> (http/request #js {:url "https://api.github.com/users/octocat" :method "GET"})
      (p/then js/console.log)))

(def statusMatrix->shortstatus
  {"000" "``"
   "003" "AD"
   "020" "??"
   "022" "A"
   "023" "AM"
   "100" "D"
   "101" "D"
   "103" "MD"
   "110" "D + ??"
   "111" "``"
   "113" "MM"
   "120" "D + ??"
   "121" "M"
   "122" "M"
   "123" "MM"})

(defn get-base-dir [] (config/get-repo-dir (state/get-current-repo)))

#_(defn get-graph-git-dir
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
  (git/init #js {:fs fs :dir graph-path})
  #_(let [_ (remove-dot-git-file! graph-path)
          separate-git-dir (get-graph-git-dir graph-path)
          args (cond-> {:fs fs :dir graph-path}
                 (git-dir-exists? graph-path) identity
                 separate-git-dir (assoc :gitdir separate-git-dir))]
      (p/let [_ (git/init (clj->js args))]
        (when utils/win32?
          (git/setConfig (clj->js (assoc args :path "core.safecrlf" :value "false")))))))

(comment
  (-> (init! base-dir)
      (p/then js/console.log)
      (p/catch js/console.error)))

(defn add-all!
  [graph-path]
  (p/let [status (git/statusMatrix #js {:fs fs :dir graph-path})
          files (map first (js->clj status))
          add #(git/add #js {:fs fs :dir graph-path :filepath %})]
    ;; TODO filter files for modified ones
    (run! add files)))

(defn commit!
  [graph-path message]
  (p/do!
   (git/setConfig #js {:fs fs :dir graph-path :path "core.quotepath" :value "false"})
   (git/commit #js {:fs fs :dir graph-path :author #js {:name "Logseq" #_#_:email "mrtest@example.com"} :message message})))

(comment
  (def base-dir (get-base-dir))
  (commit! base-dir "Auto saved by Logseq")
  (def tmp #js {:fs fs :dir base-dir :author #js {:name "Logseq" #_#_:email "mrtest@example.com"} :message "hehe"})
  (js/console.log tmp))

(defn add-all-and-commit-single-graph!
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
                    (notification/show! "Please set your git username and email first" :error)
                    (notification/show! (str error "\nIf you don't want to see those errors or don't need git, you can disable the \"Git auto commit\" feature on Settings > Version control.") :error))))))))

(defn set-author! [graph-path name email]
  (p/do!
   (git/setConfig #js {:fs fs :dir graph-path :path "user.name" :value name})
   (git/setConfig #js {:fs fs :dir graph-path :path "user.email" :value email})))

(comment
  (set-author! (get-base-dir) "Logseq" "")
  (p/extract))

(defn add-all-and-commit!
  ([]
   (add-all-and-commit! nil))
  ([message]
   (log/info ::add-all-and-commit message)
   (add-all-and-commit-single-graph! (get-base-dir) message)
   #_(doseq [path (state/get-all-graph-paths)] (add-all-and-commit-single-graph! path message))))

(defn pull! [graph-path]
  ;; TODO get ref name
  (git/pull #js {:fs fs :http http :dir graph-path :ref "main" #_#_:onAuth #(clj->js {:username pat})}))

;; let pushResult = await git.push({
;;   fs,
;;   http,
;;   dir: '/tutorial',
;;   remote: 'origin',
;;   ref: 'main',
;;   onAuth: () => ({ username: process.env.GITHUB_TOKEN }),
;; })
(defn push! [graph-path]
  ;; TODO get ref name
  (git/push #js {:fs fs :http http :dir graph-path :remote "origin" :ref "main" :onAuth (fn [] ( clj->js {:username pat}))}))

;

(comment
  (js/TextEncoder. "utf-8" )
  (git/pull #js {:fs fs :http http :dir (get-base-dir) :ref "main"})
  (git/pull #js {:fs fs :http http2 :dir (get-base-dir) :ref "main"})
  (pull! (get-base-dir))
  (push! (get-base-dir))
  (apply concat (map vec -r))
  (-> ((gobj/get http "request") #js {:url "https://api.github.com/users/octocat" :method "GET"})
      (p/then #(set! js/test %))
      (p/finally (fn [] (js/console.log "done"))))
  (set! js/test 1)
  ;; http = cljs-http.client
  (-> (.request http2 #js {:method "GET" :url "https://github.com/ofer1992/logseq_test_repo.git"})
      (p/then #(def -r %)))
  (js/console.log "haha ")
  (-> (js/fetch "https://github.com/isomorphic-git/isomorphic-git/blob/main/src/http/web/index.js" #js {:methd "GET"})
      (p/then #(def -r %)))
  (prn (js->clj -r :keywordize-keys true))
  (js/window.fetch "https://github.com/isomorphic-git/isomorphic-git/blob/main/src/http/web/index.js" #js {:methd "GET"})
  (-> (.request CapacitorHttp #js {:method "GET" :url "https://github.com/ofer1992/logseq_test_repo.git"})
      (p/then #(def -r %)))
  (js->clj -r :keywordize-keys true)
  (-> (git/listRemotes #js {:fs fs :dir (get-base-dir)})
      (p/then js/console.log))
  (git/deleteRemote #js {:fs fs :dir (get-base-dir) :remote "origin"})
  (git/addRemote #js {:fs fs :dir (get-base-dir) :remote "origin" :url "https://github.com/ofer1992/logseq_test_repo.git"})
  (use '[cljs-http.client :as http]))


(defn short-status
  [graph-path]
  ;; good enough for now?
  (p/let [status (js->clj (git/statusMatrix #js {:fs fs :dir graph-path}))
          ss (map #(vector (get statusMatrix->shortstatus (apply str (rest %))) (first %)) status)
          ss (map #(str (first %) " " (second %)) ss)
          message (string/join "\n" ss)
          changed (filter #(apply not= (rest %)) (js->clj status))
          format-fn #(str (apply gstring/format "%d%d%d %s" (concat (rest %) [(first %)])))
          formatted (map format-fn changed)]
    (notification/show! message :info)
    (string/join "\n" ss)))

(defn log
  [graph-path]
  (def graph-path (get-base-dir))
  (p/let [log (git/log #js {:fs fs :dir graph-path})
          processed (for [c  (js->clj log :keywordize-keys true)
                          :let [{:keys [oid commit]} c
                                oid (apply str (take 6 oid))
                                {:keys [message]} commit]]
                      (str oid " " message))
          message (apply str processed)]
    (notification/show! message :info true)
    log))

(comment
  (def base-dir (get-base-dir))
  (add-all! base-dir)
  (add-all-and-commit! "Auto saved by Logseq")
  (git/setConfig #js {:fs fs :dir base-dir :path "user.name" :value "ofer"})
  (commit! base-dir "Auto saved by Logseq")
  ((:readdir capacitor-backend/capacitor-fs) base-dir)
  (-> (git/log #js {:fs fs :dir base-dir})
      (p/then #(def -r %)))
  (first (js->clj -r))
  (push! (get-base-dir))


  (log (get-base-dir))
  (-> (short-status (get-base-dir))
      #_(p/then #(def -r %))
      (p/then js/console.log))
  (-> (git/statusMatrix #js {:fs fs :dir base-dir})
      (p/then js/console.log)
      #_(p/then #(def -r (js->clj %)))
      (p/catch #(js/console.error %)))
  (-> (git/log #js {:fs fs :dir base-dir})
      (p/then js/console.log))

  -r
  (vector 1 2)
  (apply str (rest (get -r 0)))
  (git/commit #js {:fs fs :dir base-dir :message "Auto saved by Logseq" :author {:name "Logseq" :email ""}})
  (-> (git/log #js {:fs fs :dir base-dir})
      (p/then js/console.log))
  (-> (git/status #js {:fs fs :dir base-dir :filepath "journals/2024_05_16.md"})
      (p/then js/console.log)))

(defonce auto-commit-interval (atom nil))
(defn- auto-commit-tick-fn
  []
  (when (not (state/disable-auto-commit?))
    (add-all-and-commit!)))

(defn configure-auto-commit!
  "Configure auto commit interval, reentrantable"
  []
  (js/console.log "in auto commit function. auto commit state: " (not (state/disable-auto-commit?)))
  (when @auto-commit-interval
    (swap! auto-commit-interval js/clearInterval))
  (when (not (state/disable-auto-commit?))
    (js/console.log "hehe")
    (let [seconds (state/auto-commit-seconds)
          millis (if (int? seconds)
                   (* seconds 1000)
                   6000)]
      (js/console.log "setting auto commit interval to " seconds " seconds")
      (log/info ::set-auto-commit-interval seconds)
      (js/setTimeout add-all-and-commit! 100)
      (reset! auto-commit-interval (js/setInterval auto-commit-tick-fn millis)))))

(defn before-graph-close-hook!
  []
  (when (and (not (state/disable-auto-commit?))
             (state/commit-on-close?))
    (add-all-and-commit!)))

(comment
  (auto-commit-tick-fn)
  (configure-auto-commit!)
  (add-all-and-commit!)
  (js/setTimeout #(js/console.log "haha") 100))
  