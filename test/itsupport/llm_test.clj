(ns itsupport.llm-test
  (:require [clojure.test :refer [deftest is]]
            [itsupport.store :as store]
            [itsupport.llm :as llm]))

(deftest route-proposal-carries-source-and-cites
  (let [db (store/seed-db)
        p (llm/infer db {:op :ticket/route :subject "tk-100" :ticket-id "tk-100"
                         :technician-id "tech-100" :hours 1.0
                         :source {:class :client-submitted-ticket :ref "demo"}})]
    (is (= :assignment-upsert (:effect p)))
    (is (= {:class :client-submitted-ticket :ref "demo"} (:source p)))
    (is (>= (:confidence p) 0.9))))

(deftest unsourced-route-proposal-carries-nil-source
  (let [db (store/seed-db)
        p (llm/infer db {:op :ticket/route :subject "tk-100" :ticket-id "tk-100"
                         :technician-id "tech-100" :hours 1.0
                         :source {:class :client-submitted-ticket :ref "demo"}
                         :unsourced? true})]
    (is (nil? (:source p)))
    (is (>= (:confidence p) 0.85) "still high-confidence — proves source-provenance cannot rely on confidence")))

(deftest disclosure-proposal-greedy-adds-extra-columns
  (let [db (store/seed-db)
        clean (llm/infer db {:op :disclosure/query :subject "tk-100" :ticket-id "tk-100"})
        greedy (llm/infer db {:op :disclosure/query :subject "tk-100" :ticket-id "tk-100" :greedy? true})]
    (is (< (count (:columns clean)) (count (:columns greedy))))))

(deftest dispute-proposal-never-marks-high-confidence
  (let [db (store/seed-db)
        p (llm/infer db {:op :dispute/request :subject "tk-100" :disputed-field :hours :claim 2.0})]
    (is (= :correction-apply (:effect p)))
    (is (< (:confidence p) 0.9))))
