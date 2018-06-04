(set-env!
 :resource-paths #{"resources"}
 :dependencies '[[cljsjs/boot-cljsjs "0.10.0" :scope "test"]])

(require '[cljsjs.boot-cljsjs.packaging :refer :all]
         '[boot.core :as boot]
         '[boot.tmpdir :as tmpd]
         '[clojure.java.io :as io]
         '[boot.util :refer [dosh]])

(def +lib-version+ "0.2.0")
(def +version+ (str +lib-version+ "-0"))
(def +github-prefix+  "https://github.com")
(def +github-org+     "material-components")
(def +github-project+ "material-components-web-react")
(def +url+ (str
            +github-prefix+ "/"
            +github-org+ "/"
            +github-project+
            "/archive/v"
            +lib-version+
            ".zip"))
(task-options!
 pom {:project     'cljsjs/material-components-react
      :version     +version+
      :description "Modular and customizable Material Design UI components for the web"
      :url         (str +github-prefix+ "/" +github-org+ "/" +github-project+ "/")
      :scm         {:url "https://github.com/cljsjs/packages"}
      :license     {"Apache-2.0" "http://opensource.org/licenses/Apache-2.0"}})

(deftask build-material-components-react []
  (let [tmp (boot/tmp-dir!)]
    (with-pre-wrap
      fileset
      ;; Copy all files in fileset to temp directory
      (doseq [f (->> fileset boot/input-files)
              :let [target (io/file tmp (tmpd/path f))]]
        (io/make-parents target)
        (io/copy (tmpd/file f) target))
      (binding [boot.util/*sh-dir* (str (io/file tmp (format "material-components-web-react-%s" +lib-version+)))]
        ;; Material-components-react uses Lerna which is quite stupid and presumes
        ;; that the project being built MUST BE a git repository: https://github.com/lerna/lerna/issues/555
        (dosh "git" "init")
        (dosh "npm" "install")
        (dosh "npm" "run" "build"))
      (-> fileset (boot/add-resource tmp) boot/commit!))))


(deftask download-mcw-react []
  (download :url +url+
            :unzip true))


(deftask package []
  (task-options! push {:ensure-branch nil})
  (comp
   (download-mcw-react)
   (build-material-components-react)

   (sift :move {#"^material-components-web-react-[^/]*/build/(.*)(!?.css)\.min\.js$"
                  "cljsjs/material-components-react/production/$1.min.inc.js"
                #"^material-components-web-react-[^/]*/build/(.*)(!?.css)\.js$"
                  "cljsjs/material-components-react/development/$1.inc.js"
                #"^material-components-web-react-[^/]*/build/(.*)\.css$"
                  "cljsjs/material-components-react/common/$1.css"})
   (deps-cljs :foreign-libs [;; Each matched file will create foreign lib entry
                             {:file #"cljsjs/material-components-react/development/(.*)(!?.min)\.inc\.js"
                              :provides ["@material/react-%1$s" "cljsjs.material-components-react.%1$s"]}]
              :externs [#"material-components-react\.ext\.js"])
   (sift :include #{#"^cljsjs" #"^deps\.cljs$"})
   (pom)
   (jar)
   (validate-checksums)))
