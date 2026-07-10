(ns itsupport.policy-contract-test
  "The governor contract as executable tests. Single invariant under test:
  TicketRouter-LLM never routes/discloses/resolves a record the
  TicketGovernor would reject."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [itsupport.store :as store]
            [itsupport.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def dispatcher {:actor-id "dp-1" :actor-role :dispatcher :phase 3})
(def manager    {:actor-id "mg-1" :actor-role :support-manager :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(deftest authorized-route-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :ticket/route :subject "tk-100" :ticket-id "tk-100"
                   :technician-id "tech-100" :hours 1.0
                   :source {:class :client-submitted-ticket :ref "demo"}}
                  dispatcher)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "tech-100" (:technician-id (store/assignment db "tk-100"))))
    (is (= 1 (count (store/ledger db))))))

(deftest unauthorized-role-is-held
  (let [[db actor] (fresh)
        res (exec-op actor "t2"
                  {:op :ticket/route :subject "tk-100" :ticket-id "tk-100"
                   :technician-id "tech-100" :hours 1.0
                   :source {:class :client-submitted-ticket :ref "demo"}}
                  {:actor-id "sub-1" :actor-role :subscriber :phase 3})]
    (is (= :hold (get-in res [:state :disposition])))
    (is (= [:rbac] (-> (store/ledger db) first :basis)))))

(deftest under-cleared-technician-is-held
  (testing "an elevated-access ticket routed to a standard-tier technician → HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :ticket/route :subject "tk-200" :ticket-id "tk-200"
                     :technician-id "tech-100" :hours 2.0
                     :source {:class :monitoring-system-alert :ref "demo"}}
                    dispatcher)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:access-tier-clearance-gate} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assignment db "tk-200"))))))

(deftest uncertified-technician-on-security-incident-is-held
  (testing "a security-incident ticket routed to a technician with no real incident-response cert → HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "t4"
                    {:op :ticket/route :subject "tk-300" :ticket-id "tk-300"
                     :technician-id "tech-250" :hours 3.0
                     :source {:class :monitoring-system-alert :ref "demo"}}
                    dispatcher)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:security-incident-misrouting-gate} (-> (store/ledger db) first :basis))))))

(deftest uncontracted-disclosure-is-held
  (let [[db actor] (fresh)
        res (exec-op actor "t5"
                  {:op :disclosure/query :subject "tk-100" :ticket-id "tk-100"}
                  {:actor-id "sub-2" :actor-role :subscriber :tenant "tenant-ghost" :phase 3})]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:licensed-disclosure} (-> (store/ledger db) first :basis)))))

(deftest over-disclosure-beyond-tier-is-held
  (let [[db actor] (fresh)
        res (exec-op actor "t6"
                  {:op :disclosure/query :subject "tk-100" :ticket-id "tk-100" :greedy? true}
                  {:actor-id "sub-1" :actor-role :subscriber :tenant "tenant-basic" :phase 3})]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:licensed-disclosure} (-> (store/ledger db) first :basis)))))

(deftest sla-urgent-route-escalates-then-human-decides
  (testing "an otherwise-clean route on a near-SLA-breach ticket interrupts for human approval"
    (let [[db actor] (fresh)
          r1 (exec-op actor "t7"
                   {:op :ticket/route :subject "tk-300" :ticket-id "tk-300"
                    :technician-id "tech-300" :hours 2.0
                    :source {:class :monitoring-system-alert :ref "demo"}}
                   dispatcher)]
      (is (= :interrupted (:status r1)))
      (is (= :sla-breach-imminent (-> r1 :state :audit last :reason)))
      (let [r2 (g/run* actor {:approval {:status :approved :by "manager-1"}}
                       {:thread-id "t7" :resume? true})]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (= "tech-300" (:technician-id (store/assignment db "tk-300"))))))))

(deftest dispute-request-always-escalates-regardless-of-confidence
  (let [[_db actor] (fresh)
        r1 (exec-op actor "t8"
                 {:op :dispute/request :subject "tk-100" :disputed-field :hours :claim 2.0}
                 manager)]
    (is (= :interrupted (:status r1)))
    (is (= :dispute-request (-> r1 :state :audit last :reason)))
    (let [r2 (g/run* actor {:approval {:status :approved :by "manager-1"}}
                     {:thread-id "t8" :resume? true})]
      (is (= :commit (get-in r2 [:state :disposition]))))))
