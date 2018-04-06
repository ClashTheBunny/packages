(set-env!
  :resource-paths #{"resources"}
  :dependencies '[[cljsjs/boot-cljsjs "0.10.0" :scope "test"]])

(require '[cljsjs.boot-cljsjs.packaging :refer :all]
         '[boot.core :as boot]
         '[boot.tmpdir :as tmpd]
         '[clojure.java.io :as io]
         '[boot.util :refer [dosh]])

(def +lib-version+ "1.5.0")
(def +version+ (str +lib-version+ "-0"))

(task-options!
  pom {:project     'cljsjs/rmwc
       :version     +version+
       :description "A React wrapper for Material Design (Web) Components"
       :url         "https://jamesmfriedman.github.io/rmwc/"
       :scm         {:url "https://github.com/jamesmfriedman/rmwc"}
       :license     {"BSD" "https://opensource.org/licenses/MIT"}})

(deftask build-rmwc []
  (let [tmp (boot/tmp-dir!)]
    (with-pre-wrap
      fileset
      ;; Copy all files in fileset to temp directory
      (doseq [f (->> fileset boot/input-files)
              :let [target (io/file tmp (tmpd/path f))]]
        (io/make-parents target)
        (io/copy (tmpd/file f) target))
      (binding [boot.util/*sh-dir* (str (io/file tmp (format "rmwc-%s" +lib-version+)))]
        (dosh "npm" "install")
        (dosh "npm" "run" "babelfy")
        (dosh "npm" "run" "build:lib"))
      (-> fileset (boot/add-resource tmp) boot/commit!))))

(deftask package []
  (task-options! push {:ensure-branch nil})
  (comp
   (download :url (str "https://github.com/jamesmfriedman/rmwc/archive/v" +lib-version+ ".zip")
             :unzip true)

   (build-rmwc)

   (sift :move {#"^rmwc-[^/]*/lib/rmwc.umd.js$"        "cljsjs/rmwc/development/rmwc.inc.js"
                #"^rmwc-[^/]*/index.js$"               "cljsjs/rmwc/development/index.js"
                #"^rmwc-[^/]*/([A-Z][^/]*)/([^/]*js)$" "cljsjs/rmwc/development/$1/$2"})

   (sift :include #{#"^cljsjs"})
   (deps-cljs :name "cljsjs.material-components")
   (pom :project 'cljsjs/rmwc
        :dependencies [['cljsjs/react "16.3.0-1"]
                       ['cljsjs/react-dom "16.3.0-1"]
                       ['cljsjs/material-components "0.34.1-0"]])
   (jar)
   (validate-checksums))))
