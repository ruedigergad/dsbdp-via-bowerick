;;;
;;;   Copyright 2017, Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns
  ^{:author "Ruediger Gad",
    :doc "Main class to start the dsbdp via bowerick stand-alone application."}
  dsbdp-via-bowerick.main
  (:require
    [bowerick.jms :refer :all]
;    [cli4clj.cli :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.pprint :refer :all]
    [clojure.string :as s]
    [clojure.tools.cli :refer :all]
    [dsbdp.data-processing-dsl :refer :all])
  (:gen-class))

(defn start-dsbdp-via-bowerick
  [arg-map]
  (let [shutdown-fn (fn [])]
    (if (arg-map :daemon)
        (-> (agent 0) (await))
        (do
          (println "dsbdp-via-bowerick started... Type \"q\" followed by <Return> to quit: ")
          (while (not= "q" (read-line))
            (println "Type \"q\" followed by <Return> to quit: "))
          (shutdown-fn)))))

(defn -main [& args]
  (let [cli-args (cli args
                   ["-d" "--daemon" "Run as daemon." :flag true :default false]
                   ["-h" "--help" "Print this help." :flag true]
                   ["-i" "--input-destination"
                    "Name of the destination from which the input data will be read."
                    :default "/topic/bowerick.message.generator"]
                   ["-o" "--output-destination-prefix"
                    (str "Prefix of the destination to which the ouput will be sent.\n"
                         "The output will be sent to \"<prefix>.out\"."
                         " For the default setting, this means that the output will be sent to: /topic/dsbdp.transformation_1.out\n"
                         "In addition, destinations for management purposes will be created."
                         " These destinations are named \"<prefix>.management.in\", for sending commands, and \"<prefix>.management.out\", for receiving status information and replies.")
                    :default "/topic/dsbdp.transformation_1"]
                   ["-u" "--url"
                     "URL for connecting to the broker."
                     :default "tcp://localhost:61616"])
        arg-map (cli-args 0)
        extra-args (cli-args 1)
        help-string (cli-args 2)]
    (if (arg-map :help)
      (do
        (println "dsbdp-via-bowerick help:")
        (println help-string))
      (do
        (binding [*out* *err*]
          (println "Starting dsbdp-via-bowerick with the following options:")
          (pprint arg-map)
          (pprint extra-args)
          (start-dsbdp-via-bowerick arg-map))))))

