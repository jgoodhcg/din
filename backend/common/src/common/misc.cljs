(ns common.misc
  (:require [applied-science.js-interop :as j]))

(defn log [& args] (-> js/console (j/get :log) (apply args)))

(defn log-debug [& args] (-> js/console (j/get :debug) (apply args)))

(defn log-error [& args] (-> js/console (j/get :error) (apply args)))

(def error-msg "Captain, we have a problem ...")

(defn get-envvar [envar-key] (-> js/process (j/get :env) (j/get envar-key)))
