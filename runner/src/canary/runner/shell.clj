(ns canary.runner.shell
  "High-level utils for working with shell."
  (:require [me.raynes.conch.low-level :as sh]
            [me.raynes.fs :as fs]
            [canary.runner.output :as output]
            [canary.runner.print :refer [announce]]
            [canary.runner.utils :as utils]
            [clojure.java.io :as io]))

; -- helpers ----------------------------------------------------------------------------------------------------------------

(defn stream-proc-output! [proc]
  (output/print-stream-as-lines! (:out proc) output/synchronized-out-printer)
  (output/print-stream-as-lines! (:err proc) output/synchronized-err-printer))

(defn determine-workdir-for-task [task options]
  (let [job-slug (utils/sanitize-as-filename (or (:job-id options) "_local-job"))
        task-slug (utils/sanitize-as-filename (:name task))
        task-workdir (str (:workdir options) "/" "jobs" "/" job-slug "/" "tasks" "/" task-slug)]
    task-workdir))

(defn ensure-clean-workdir! [path]
  (when (fs/exists? path)
    (assert (fs/directory? path))
    (fs/delete-dir path))
  (fs/mkdirs path))

(defn prepare-workdir! [task options]
  (let [workdir-path (determine-workdir-for-task task options)]
    (ensure-clean-workdir! workdir-path)
    workdir-path))

(defn make-shell-launcher [file & [env]]
  (let [path (str file)
        name (.getName file)]
    (fn [options]
      (let [task (meta options)]
        (let [workdir (prepare-workdir! task options)
              proc (sh/proc path :verbose false :dir workdir :env env)]
          (stream-proc-output! proc)
          (let [status (sh/exit-code proc)]
            (announce (str "shell task " name " exit-code: " status) 2 options)
            {:exit-code status}))))))

(defn extract-outputs-if-needed [result proc options]
  (if (:stream-output options)
    result
    (assoc result
      :out (sh/stream-to-string proc :out)
      :err (sh/stream-to-string proc :err))))

(defn launch! [cmd args & [options]]
  (let [proc (apply sh/proc cmd args)]
    (when (:stream-output options)
      (stream-proc-output! proc))
    (let [status (sh/exit-code proc)]
      (-> {:exit-code status}
          (extract-outputs-if-needed proc options)))))
