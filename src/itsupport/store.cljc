(ns itsupport.store
  "SSoT for the IT-support ticket-routing actor, behind a `Store` protocol
  so the backend is a swap, not a rewrite:

    - `MemStore`     — atom of Datomic-shaped EDN. The deterministic default
                       for dev/tests/demo (no deps).
    - `DatomicStore` — backed by `langchain.db`, a Datomic-API-compatible EAV
                       store. Pure `.cljc`, so it runs offline AND can be
                       pointed at a real Datomic Local or a kotoba-server pod.

  Both implement the same protocol and pass the same contract
  (test/itsupport/store_contract_test.clj) — the actor, the TicketGovernor
  and the audit ledger never know which SSoT they run on.

  Entity shapes: a technician (access-tier, certifications), a ticket
  (category/required-access-tier/sla-remaining-minutes), an assignment
  (ticket→technician, committed), a subscriber contract (tenant × tier).
  There is NO field anywhere in this schema for payroll, timesheet
  approval, or employer-of-record status — this actor only routes tickets
  to already-contracted operators, mirroring `cloud-itonami-isic-8299`'s
  boundary (never employer-of-record, distinct from `cloud-itonami-
  isic-7820`'s dispatch model).

  The ledger stays append-only on every backend."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [langchain.db :as d]))

(defprotocol Store
  (technician [s id])
  (all-technicians [s])
  (ticket [s id])
  (all-tickets [s])
  (assignment [s ticket-id])
  (contract [s tenant])
  (ledger [s])
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision/disclosure fact")
  (with-technicians [s technicians] "replace/seed technicians (map id→technician)")
  (with-tickets [s tickets]         "replace/seed tickets (map id→ticket)")
  (with-contracts [s contracts]     "replace/seed subscriber contracts (map tenant→contract)"))

;; ───────────────────────── demo data (fictitious) ─────────────────────

(defn demo-data
  "A small, entirely fictitious dataset so the actor + tests run offline.
  `tk-300` carries a near-breach `:sla-remaining-minutes` purely to
  exercise the sla-breach-imminent governor gate."
  []
  {:technicians
   {"tech-100" {:id "tech-100" :name "山田 一郎(デモ)" :access-tier :tier/standard
                :certifications #{}}
    "tech-200" {:id "tech-200" :name "Jane Smith (demo)" :access-tier :tier/elevated
                :certifications #{:cissp}}
    "tech-250" {:id "tech-250" :name "佐藤 次郎(デモ)" :access-tier :tier/elevated
                :certifications #{}}
    "tech-300" {:id "tech-300" :name "鈴木 花子(デモ)" :access-tier :tier/privileged
                :certifications #{:giac-gcih}}}
   :tickets
   {"tk-100" {:id "tk-100" :client "tenant-acme" :category :general
              :required-access-tier :tier/standard :sla-remaining-minutes 240}
    "tk-200" {:id "tk-200" :client "tenant-acme" :category :elevated-access
              :required-access-tier :tier/elevated :sla-remaining-minutes 120}
    "tk-300" {:id "tk-300" :client "tenant-acme" :category :security-incident
              :required-access-tier :tier/elevated :sla-remaining-minutes 30}}
   :contracts
   {"tenant-acme"  {:tenant "tenant-acme" :tier :tier/pro :active? true :purpose :managed-services}
    "tenant-basic" {:tenant "tenant-basic" :tier :tier/basic :active? true :purpose :helpdesk-only}}})

;; ───────────────────────── MemStore (default) ─────────────────────────

(defrecord MemStore [a]
  Store
  (technician [_ id] (get-in @a [:technicians id]))
  (all-technicians [_] (sort-by :id (vals (:technicians @a))))
  (ticket [_ id] (get-in @a [:tickets id]))
  (all-tickets [_] (sort-by :id (vals (:tickets @a))))
  (assignment [_ ticket-id] (get-in @a [:assignments ticket-id]))
  (contract [_ tenant] (get-in @a [:contracts tenant]))
  (ledger [_] (:ledger @a))
  (commit-record! [s {:keys [effect path value]}]
    (case effect
      :assignment-upsert (swap! a assoc-in [:assignments (:ticket-id value)] value)
      :correction-apply  (swap! a update-in [:assignments (first path)] merge (:patch value))
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-technicians [s ts] (when (seq ts) (swap! a assoc :technicians ts)) s)
  (with-tickets [s tks]    (when (seq tks) (swap! a assoc :tickets tks)) s)
  (with-contracts [s cts]  (when (seq cts) (swap! a assoc :contracts cts)) s))

(defn seed-db
  "A MemStore seeded with the demo data. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data) :assignments {} :ledger []))))

;; ───────────────────────── DatomicStore (langchain.db) ─────────────────

(def ^:private schema
  {:technician/id   {:db/unique :db.unique/identity}
   :ticket/id       {:db/unique :db.unique/identity}
   :assignment/ticket-id {:db/unique :db.unique/identity}
   :contract/tenant {:db/unique :db.unique/identity}
   :ledger/seq      {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- technician->tx [{:keys [id name access-tier certifications]}]
  (cond-> {:technician/id id}
    name            (assoc :technician/name name)
    access-tier     (assoc :technician/access-tier access-tier)
    true            (assoc :technician/certifications (enc (or certifications #{})))))

(defn- pull->technician [m]
  (when (:technician/id m)
    {:id (:technician/id m) :name (:technician/name m)
     :access-tier (:technician/access-tier m)
     :certifications (or (dec* (:technician/certifications m)) #{})}))

(def ^:private technician-pull
  [:technician/id :technician/name :technician/access-tier :technician/certifications])

(defn- ticket->tx [{:keys [id client category required-access-tier sla-remaining-minutes]}]
  (cond-> {:ticket/id id}
    client                  (assoc :ticket/client client)
    category                (assoc :ticket/category category)
    required-access-tier    (assoc :ticket/required-access-tier required-access-tier)
    sla-remaining-minutes   (assoc :ticket/sla-remaining-minutes sla-remaining-minutes)))

(defn- pull->ticket [m]
  (when (:ticket/id m)
    {:id (:ticket/id m) :client (:ticket/client m) :category (:ticket/category m)
     :required-access-tier (:ticket/required-access-tier m)
     :sla-remaining-minutes (:ticket/sla-remaining-minutes m)}))

(def ^:private ticket-pull
  [:ticket/id :ticket/client :ticket/category :ticket/required-access-tier
   :ticket/sla-remaining-minutes])

(defn- assignment->tx [{:keys [ticket-id technician-id hours source]}]
  {:assignment/ticket-id ticket-id :assignment/technician-id technician-id
   :assignment/hours hours :assignment/source (enc source)})

(defn- pull->assignment [m]
  (when (:assignment/ticket-id m)
    {:ticket-id (:assignment/ticket-id m) :technician-id (:assignment/technician-id m)
     :hours (:assignment/hours m) :source (dec* (:assignment/source m))}))

(def ^:private assignment-pull
  [:assignment/ticket-id :assignment/technician-id :assignment/hours :assignment/source])

(defn- contract->tx [{:keys [tenant tier active? purpose]}]
  {:contract/tenant tenant :contract/tier tier :contract/active active? :contract/purpose purpose})

(defn- pull->contract [m]
  (when (:contract/tenant m)
    {:tenant (:contract/tenant m) :tier (:contract/tier m)
     :active? (:contract/active m) :purpose (:contract/purpose m)}))

(def ^:private contract-pull
  [:contract/tenant :contract/tier :contract/active :contract/purpose])

(defrecord DatomicStore [conn]
  Store
  (technician [_ id] (pull->technician (d/pull (d/db conn) technician-pull [:technician/id id])))
  (all-technicians [_]
    (->> (d/q '[:find [?id ...] :where [?e :technician/id ?id]] (d/db conn))
         (map #(pull->technician (d/pull (d/db conn) technician-pull [:technician/id %])))
         (sort-by :id)))
  (ticket [_ id] (pull->ticket (d/pull (d/db conn) ticket-pull [:ticket/id id])))
  (all-tickets [_]
    (->> (d/q '[:find [?id ...] :where [?e :ticket/id ?id]] (d/db conn))
         (map #(pull->ticket (d/pull (d/db conn) ticket-pull [:ticket/id %])))
         (sort-by :id)))
  (assignment [_ ticket-id]
    (pull->assignment (d/pull (d/db conn) assignment-pull [:assignment/ticket-id ticket-id])))
  (contract [_ tenant] (pull->contract (d/pull (d/db conn) contract-pull [:contract/tenant tenant])))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (commit-record! [s {:keys [effect path value]}]
    (case effect
      :assignment-upsert (d/transact! conn [(assignment->tx value)])
      :correction-apply
      (d/transact! conn [(assignment->tx (merge (assignment s (first path)) (:patch value)))])
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-technicians [s ts]
    (when (seq ts) (d/transact! conn (mapv technician->tx (vals ts)))) s)
  (with-tickets [s tks]
    (when (seq tks) (d/transact! conn (mapv ticket->tx (vals tks)))) s)
  (with-contracts [s cts]
    (when (seq cts) (d/transact! conn (mapv contract->tx (vals cts)))) s))

(defn datomic-store
  ([] (datomic-store {}))
  ([{:keys [technicians tickets contracts]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (-> s (with-technicians technicians) (with-tickets tickets) (with-contracts contracts)))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo data — proves protocol parity."
  []
  (datomic-store (demo-data)))

;; ───────────────────────── ledger formatting ─────────────────────────

(defn ledger-line
  [{:keys [op actor subject disposition basis]}]
  (str/join " · "
            [(name disposition)
             (str "op=" op)
             (str "actor=" actor)
             (str "subject=" subject)
             (str "basis=" (pr-str basis))]))
