(ns itsupport.store-contract-test
  (:require [clojure.test :refer [deftest is testing]]
            [itsupport.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= :tier/standard (:access-tier (store/technician s "tech-100"))))
      (is (= #{:cissp} (:certifications (store/technician s "tech-200"))))
      (is (= :security-incident (:category (store/ticket s "tk-300"))))
      (is (= 30 (:sla-remaining-minutes (store/ticket s "tk-300"))))
      (is (= 4 (count (store/all-technicians s))))
      (is (= 3 (count (store/all-tickets s)))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "assignment upsert commits"
        (store/commit-record! s {:effect :assignment-upsert
                                 :value {:ticket-id "tk-100" :technician-id "tech-100"
                                         :hours 1.5
                                         :source {:class :client-submitted-ticket :ref "demo"}}})
        (is (= "tech-100" (:technician-id (store/assignment s "tk-100")))))
      (testing "correction-apply patches the assignment"
        (store/commit-record! s {:effect :correction-apply
                                 :value {:patch {:hours 3.0}}
                                 :path ["tk-100"]})
        (is (= 3.0 (:hours (store/assignment s "tk-100")))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (take-last 2 (store/ledger s)))))))))

(deftest contract-lookup
  (doseq [[label s] (backends)]
    (testing label
      (is (= :tier/pro (:tier (store/contract s "tenant-acme"))))
      (is (nil? (store/contract s "tenant-ghost"))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/technician s "nope")))
    (is (= [] (store/all-technicians s)))
    (is (= [] (store/ledger s)))))
