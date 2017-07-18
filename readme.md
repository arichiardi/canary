# Canary in a coal mine

This project provides a tool for exercising participating projects with a pinned bleeding-edge ClojureScript compiler version. 
The goal is to have some smoke tests which allow us to detect issues early before releasing a new ClojureScript version.

Canary provides a script which can be used to run a job. Each job is assigned a ClojureScript compiler version. 
It builds compiler jar, uploads it and then runs exercises of individual projects in parallel (we call them tasks). 
Finally it waits for task results and generates a report for archiving.

Typically you don't run canary script on local machine but rather invoke it on some machine in the cloud. 
For your convenience a new job can be triggered by committing an empty commit into the [jobs branch](https://github.com/cljs-oss/canary/tree/jobs).
Travis machine will then execute canary script possibly triggering more child Travis builds for individual project tasks.
At the end, results are generated and archived in the [results branch](https://github.com/cljs-oss/canary/tree/results).
Also look into [GitHub releases of this repo](https://github.com/cljs-oss/canary/releases) where individual built compiler versions get published.

## Quick test

### via docker

```bash
./scripts/docker-build.sh
./scripts/docker-run.sh job --help
./scripts/docker-run.sh job -v
```

### directly

```bash
./runner/run.sh job -v -r afe65a0
```

### Vocabulary

* the script is called `runner`
* ClojureScript compiler is usually simply referred to as the `compiler`
* a list of relevant projects/libraries is called `projects`
* a single request for a complete round of tests is called a `job`
* a single project test is called a `task`

## Main ideas

### A single repo

Instead of maintaining multiple git repos, let's have just a single mono-repo `canary` with following branches:

1. `master` where the source code for the `runner` lives
2. `jobs` where anyone with commit rights can trigger a new `job` (as a new commit)
3. `results` where the `runner` will produce one commit per `job`

All participating project authors will get commit access to this repo and can collaborate on the `master` branch.

### The job workflow

When a `job` is triggered. The `runner` goes through following steps:

1. prepares `compiler`'s jar according to requested parameters (repo/version)
1. determines which `tasks` should be part of the `job` based on request parameters
1. spawns all `tasks` (in parallel) instructing them to use the `compiler`
1. waits for `tasks` results and collects them 
1. generates a report and commits it `results` branch

### Implemented in Clojure

The `runner` is implemented in Clojure for convenience. Each `project` author gets own place (function/script)
for implementing functionality specific for their `project`. Authors are expected to trigger test builds on their own repos 
(e.g. via Travis) and simply collect results back. But anyone can use an escape hatch to invoke a shell script and do whatever 
they need in Clojure. For example cloning their repo and running tests directly in the context of the `runner`.

### Docker

We wrap `runner` in a Docker container to provide well-controlled environment for `tasks` (possibly shell scripts).

### No need for fancy publishing 

We are developers and we have git and GitHub which is a great publishing tool on its own. Travis alone will provide some trace 
of a `job` (depending on what `runner` script and individual `tasks` output to stdout). But ultimately a commit into `results` 
branch presents all interesting results also for archivation purposes. Then anyone can use their git-fu to follow those or 
process them further.

## FAQ

#### How to run a job?

Please read the readme in the [jobs branch](https://github.com/cljs-oss/canary/tree/jobs) with details on that.  

#### How to run a job with specific ClojureScript fork?

No problem. You can point Canary to your own fork of ClojureScript by specifying `--compiler-repo` and `--compiler-rev` parameters. 

#### How can I participate with my project?

You will need to write a new task for your project. First look how existing [projects are implemented](https://github.com/cljs-oss/canary/tree/master/runner/src/canary/projects).
Ask for commit access. You can write your task as a Clojure function or as a shell script.

In case of a Clojure function you have to annotate it with `^:task` metadata so that runner recognizes it. In general
you can do whatever you need to do in your task (it is running on a separate thread) - this will depend you your project setup. 
We provide some shared helper functions. For example `canary.runner.travis` namespace might be very useful for triggering 
a Travis build for your own project. For your inspiration [here](https://github.com/cljs-oss/canary/blob/master/runner/src/canary/projects/binaryage.clj) 
is the task for cljs-devtools, and [here](https://github.com/binaryage/cljs-devtools/commit/45c1df1e0de53c9d320963b296bd7a741056599c) 
is the adaptation needed in the project itself. Please note that child Travis build triggered by `travis/request-build!`
is configured with bunch of extra env variables prefixed with `CANARY_` (an example [here](https://travis-ci.org/binaryage/cljs-devtools/jobs/254939442/config)) those have to be taken into account by
the participating project.

In case of a shell script. You simply create `your_name.sh` in the projects directory. Script has to return with zero exit
code to be considered as passing. Standard outputs will be embedded into the final report, so don't be too verbose there.

You can test your task locally. Running jobs without `--production` flag should do no harm. When using `travis/request-build!` 
it will mock it by default. You will have to add `--production` as the last step for fine-tuning final version of the code.

---

### Credits

Inspired by ideas of [@deraen](https://gist.github.com/Deraen/f3b25fd459b6af134c0836cde36fb3fb) and [@mfikes](https://gist.github.com/mfikes/8be162fd730774e11990255345ee1127).

You might discuss this in #cljs-dev channel on [clojurians.net](http://clojurians.net) Slack.
