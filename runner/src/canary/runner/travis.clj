(ns canary.runner.travis
  "Supporting Travis functionality for tasks."
  (:require [me.raynes.conch.low-level :as sh]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [canary.runner.shell :as shell]
            [canary.runner.print :refer [announce]]
            [canary.runner.env :as env]
            [canary.runner.utils :as utils])
  (:import (java.net URLEncoder)))

(defn api-invalid-json-response-msg [error response]
  (str "Travis API responded with invalid JSON: " error "\n" response))

(defn api-token-not-set-msg [token-var-name]
  (str "Travis API token '" token-var-name "' not set in the environment"))

(defn curl-failed-msg [error]
  (str "curl failed when talking to travis\n" error))

(defn blind-secrets [args]
  (let [* (fn [arg]
            (if (re-matches #"^Authorization:.*" arg)
              "Authorization: <secret>"
              arg))]
    (vec (map * args))))

(defn make-build-url [slug request-id]
  ; TODO: we will have to query travis API to get list of builds triggered by this request id
  (str "https://travis-ci.org/" slug "/builds/"))

(defn parse-response [content]
  (try
    (json/read-str content)
    (catch Throwable e
      (throw (ex-info (api-invalid-json-response-msg (.getMessage e) content) {:response content
                                                                               :error    (.getMessage e)})))))

(defn inspect-api-response [response-text]
  (parse-response response-text))

(defn post-to-travis-api! [api-endpoint token request-body options]
  (let [curl-args ["-s" "-X" "POST"
                   "-H" "Content-Type: application/json"
                   "-H" "Accept: application/json"
                   "-H" "Travis-API-Version: 3"
                   "-H" (str "Authorization: token " token)
                   "-d" (json/write-str request-body)
                   api-endpoint]]
    (announce (str "> curl " (blind-secrets curl-args)) 1 options)
    (let [result (shell/launch! "curl" curl-args)]
      (if (and (zero? (:exit-code result)) (empty? (:err result)))
        (inspect-api-response (:out result))
        (throw (ex-info (curl-failed-msg (:err result)) {:result result}))))))

(defn prepare-build-env [options]
  (let [{:keys [build-id build-download-url travis-job-url]} (:build-result options)]
    {"CANARY_BUILD"                 "1"
     "CANARY_CLOJURESCRIPT_VERSION" build-id
     "CANARY_CLOJURESCRIPT_JAR_URL" build-download-url
     "CANARY_TRAVIS_JOB_URL"        travis-job-url}))

(defn trigger-build-with-token! [slug token branch options]
  (let [api-slug (URLEncoder/encode slug)
        api-endpoint (str "https://api.travis-ci.org/repo/" api-slug "/requests")
        build-result (:build-result options)
        body {:request
              {:branch  branch
               :message (str "canary build with ClojureScript " (:build-id build-result))
               :config  {:merge_mode "deep_merge"
                         :env        (prepare-build-env options)}}}
        request-body (merge body (:travis-body options))
        response (post-to-travis-api! api-endpoint token request-body options)
        repo-slug (get-in response ["repository" "slug"])
        request-id (get-in response ["request" "repository" "id"])
        build-url (make-build-url repo-slug request-id)
        report (str "triggered a build of " repo-slug " => request id " request-id)]
    (announce (str "travis response:\n" (utils/pp response)) 2 options)
    {:status :ok
     :report report}))

(defn trigger-build! [slug token-var-name branch options]
  (announce (str "trigger-build! " slug " " token-var-name " " branch "\n" (utils/pp options)) 1 options)
  (if-some [api-token (env/get token-var-name)]
    (trigger-build-with-token! slug api-token branch options)
    (throw (ex-info (api-token-not-set-msg token-var-name)
                    {:token-var-name token-var-name
                     :slug           slug
                     :options        options}))))
