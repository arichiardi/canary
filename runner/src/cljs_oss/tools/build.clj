(ns cljs-oss.tools.build
  "Special task for building ClojureScript compiler."
  (:require [cljs-oss.tools.print :as print :refer [announce with-task-printing]]
            [cljs-oss.tools.utils :as utils]
            [cljs-oss.tools.shell :as shell]
            [clojure.java.io :as io]
            [cljs-oss.tools.env :as env]
            [clojure.edn :as edn]))

(def build-script-path "scripts/build_compiler.sh")

(defn make-result-file-problem-msg [path reason]
  (str "Unable to to read env edn file at '" path "'" (if (some? reason) (str ", reason: " reason "."))))

(defn read-build-result [options]
  (let [workdir (:workdir options)
        result-file-path (utils/canonical-path (str workdir "/result.edn"))
        edn-file (io/file result-file-path)]
    (try
      (let [data (edn/read-string (slurp edn-file))]
        (assert (map? data))
        data)
      (catch Throwable e
        (let [reason (.getMessage e)
              info {:path   result-file-path
                    :file   edn-file
                    :reason reason}]
          (throw (ex-info (make-result-file-problem-msg result-file-path reason) info)))))))

(defn build-compiler! [build-task compiler-rev compiler-repo options]
  ; note it seemed to be easier to resort to shell for building the compiler
  (let [script (io/file (utils/canonical-path build-script-path))
        env {"COMPILER_REPO"     compiler-repo
             "COMPILER_REV"      compiler-rev
             "RESULT_DIR"        (:workdir options)
             "CANARY_VERBOSITY"  (str (:verbosity options))
             "CANARY_REPO_TOKEN" (env/get "CANARY_REPO_TOKEN")}                                                               ; we want to get advantage of .env files
        build-launcher (shell/make-shell-launcher script env)
        result (build-launcher (with-meta options build-task))
        exit-code (:exit-code result)]
    (if (zero? exit-code)
      (read-build-result options)
      (do
        (announce (str "compiler build failed wit exit code " exit-code))
        nil))))

(defn prepare-compiler! [options]
  (let [{:keys [compiler-rev compiler-repo]} options]
    (announce (str (print/emphasize "building") " compiler rev " compiler-rev " from " compiler-repo))
    (let [build-task {:name  "compiler build"
                      :color :cyan}]
      (with-task-printing build-task options
        (build-compiler! build-task compiler-rev compiler-repo options)))))
