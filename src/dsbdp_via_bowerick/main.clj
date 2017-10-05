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

(def pcap-processing-dsl-expression
  {:output-type :json-str
   :rules [['pcap-off '(int 16)]
           ['off '(cond
                    (= 2 (int32be (+ pcap-off 0))) (+ pcap-off 4)
                    (and
                      (or
                        (= 0 (int16 (+ pcap-off 0)))
                        (= 4 (int16 (+ pcap-off 0))))
                      (= 1 (int16 (+ pcap-off 2)))
                      (= 0x800 (int16 (+ pcap-off 14)))) (+ pcap-off 16)
                    :default (+ pcap-off 14))]
           ['protocol '(str "Ethernet")]
           ['dst '(eth-mac-addr-str 0)]
           ['src '(eth-mac-addr-str 6)]
           ['data [['protocol '(str "IPv4")]
                   ['len '(int16 (+ off 2))]
                   ['src '(ipv4-addr-str (+ off 12))]
                   ['dst '(ipv4-addr-str (+ off 16))]
                   ['protocol-id '(int8 (+ off 9))]
                   ['data [
                           '(= 17 __1_protocol-id)
                             [['protocol '(str "UDP")]
                              ['src '(int16 (+ off 20))]
                              ['dst '(int16 (+ off 22))]
                              ;['summary '(str __2_protocol ": " __2_src " -> " __2_dst)]
                              
                              ]
                           '(= 6 __1_protocol-id)
                             [['protocol '(str "TCP")]
                              ['src '(int16 (+ off 20))]
                              ['dst '(int16 (+ off 22))]
                              ['flags-value '(int8 (+ off 33))]
;                              ['flags '(reduce-kv
;                                         #=(eval
;                                             `(fn [r# k# v#]
;                                                (cond
;                                                  (> (bit-and ~'__2_flags-value (bit-shift-left 1 k#)) 0)
;                                                    (conj r# v#)
;                                                  :default r#)))
;                                         #{}
;                                         ["FIN" "SYN" "RST" "PSH" "ACK" "URG" "ECE" "CWR"])]
;                              ['summary '(str __2_protocol __2_flags ": " __2_src " -> " __2_dst)]
                              ;['summary '(str __2_protocol ": " __2_src " -> " __2_dst)]
                              
                              ]
                         '(= 1 __1_protocol-id)
                           [['protocol '(str "ICMP")]
                            ['type '(condp = (int8 (+ off 20))
                                      0 "Echo Reply"
                                      3 "Destination Unreachable"
                                      8 "Echo Request"
                                     (str "Unknown ICMP Type:" (int8 (+ off 20))))]
                            ['seq-no '(int16 (+ off 26))]
                            ;['summary '(str "ICMP: " __2_type ", Seq.: " __2_seq-no)]
                            
                            ]]]
                  ;['summary '(str __1_protocol ": " __1_src " -> " __1_dst)]
                  
                  ]]
           ;['summary '(str protocol ": " src " -> " dst)]
           
           ]})

(defn start-dsbdp-via-bowerick
  [arg-map]
  (let [tmp-expression (arg-map :dsl-expression)
        _ (do (println "Using DSL expression:")
              (pprint tmp-expression)
              (println "---"))
        dsl-expression (cond
                         (= 'pcap tmp-expression) pcap-processing-dsl-expression
                         :default tmp-expression)
        processing-fn (atom (create-proc-fn dsl-expression))
        broker-url (arg-map :url)
        out-prefix (arg-map :output-destination-prefix)
        out-destination (str out-prefix ".out")
        pool-size (arg-map :pool-size)
        out-producer (condp = (arg-map :output-type)
                       "json" (create-json-producer broker-url out-destination pool-size)
                       "plain" (create-producer broker-url out-destination pool-size)
                       (do
                         (println "Invalid output type:" (arg-map :output-type))
                         (println "Defaulting to JSON output.")
                         (create-json-producer broker-url out-destination pool-size)))
        in-destination (arg-map :input-destination)
        in-consume-fn (fn [in-data]
                        (let [out-data (@processing-fn in-data)]
                          (out-producer out-data)))
        in-consumer (condp = (arg-map :input-type)
                      "json" (create-json-consumer broker-url in-destination in-consume-fn pool-size)
                      "plain" (create-consumer broker-url in-destination in-consume-fn pool-size)
                      (do
                        (println "Invalid input type:" (arg-map :input-type))
                        (println "Defaulting to plain consumer.")
                        (create-consumer broker-url in-destination in-consume-fn pool-size)))
        shutdown-fn (fn []
                      (close in-consumer)
                      (close out-producer))]
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
                   ["-e" "--dsl-expression"
                    "The dsbdp DSL expression from which the data processing function is created."
                    :default {:output-type :clj-map :rules [['input-type '(str (type (identity))) :string]
                                                             ['input-data '(str (identity)) :string]]}
                    :parse-fn (binding [*read-eval* false] #(read-string %))]
                   ["-h" "--help" "Print this help." :flag true]
                   ["-i" "--input-destination"
                    "Name of the destination from which the input data will be read."
                    :default "/topic/bowerick.message.generator"]
                   ["-I" "--input-type"
                    "The consumer type for ingesting the input data. Choices: \"json\", \"plain\""
                    :default "plain"]
                   ["-o" "--output-destination-prefix"
                    (str "Prefix of the destination to which the ouput will be sent.\n"
                         "The output will be sent to \"<prefix>.out\"."
                         " For the default setting, this means that the output will be sent to: /topic/dsbdp.transformation_1.out\n"
                         "In addition, destinations for management purposes will be created."
                         " These destinations are named \"<prefix>.management.in\", for sending commands, and \"<prefix>.management.out\", for receiving status information and replies.")
                    :default "/topic/dsbdp.transformation_1"]
                   ["-O" "--output-type"
                    "The producer type for emitting the output data. Choices: \"json\", \"plain\"."
                    :default "plain"]
                   ["-p" "--pool-size"
                    "The bowerick consumer/producer pool size for input/output."
                    :default 1
                    :parse-fn #(Integer/decode %)]
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

