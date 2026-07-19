(ns itsupport.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300
  Wave5 rollout ledger): this repo previously had NO demo page and no
  generator at all. This namespace drives the REAL actor stack
  (`itsupport.operation` -> `itsupport.policy` -> `itsupport.store`)
  through a scenario adapted from this repo's own `itsupport.sim` demo
  driver (`clojure -M:dev:run`, confirmed by actually running it that its
  ids match `itsupport.store/demo-data` before this file was written --
  unlike `cloud-itonami-isic-851`'s broken `schoolops.sim`, this repo's
  own sim driver is correct), trimmed to a representative subset (one
  auto-commit, two escalate->approve->commit lifecycles, and three
  distinct HARD-hold reasons) and rendered deterministically -- no
  invented numbers, no timestamps in the page content, byte-identical
  across reruns against the same seed (verified by diffing two
  consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [itsupport.store :as store]
            [itsupport.operation :as op]
            [langgraph.graph :as g]))

(def ^:private dispatcher
  {:actor-id "dp-1" :actor-role :dispatcher :phase 3})

(def ^:private manager
  {:actor-id "mg-1" :actor-role :support-manager :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "mg-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: tk-100 routes cleanly to standard-tier tech-100
  (auto-commit -- phase 3, governor-clean, no capital/SLA risk); tk-100
  is then re-proposed with the feed's source stripped (HARD hold ·
  source-provenance-gate, never reaches a human); tk-200 (elevated-access)
  is proposed to under-cleared tech-100 (HARD hold ·
  access-tier-clearance-gate); tk-300 (security-incident) is proposed to
  tech-250, who holds no named incident-response certification (HARD
  hold · security-incident-misrouting-gate); tk-300 is then correctly
  routed to privileged, GIAC-GCIH-certified tech-300 -- SLA remaining
  (30min) is inside the breach-imminent threshold, so it ALWAYS escalates
  regardless of confidence (approved); finally a dispute is filed against
  the tk-100 assignment's hours, which ALWAYS escalates on any phase
  (approved). Returns the resulting store -- every field read by `render`
  below is real governor/store output, not a hand-typed copy."
  []
  (let [db    (store/seed-db)
        actor (op/build db)]
    (exec! actor "t1-route" {:op :ticket/route :subject "tk-100" :ticket-id "tk-100"
                              :technician-id "tech-100" :hours 1.5
                              :source {:class :client-submitted-ticket :ref "portal:tk-100"}}
           dispatcher)

    (exec! actor "t2-unsourced" {:op :ticket/route :subject "tk-100" :ticket-id "tk-100"
                                  :technician-id "tech-100" :hours 1.0
                                  :source {:class :client-submitted-ticket :ref "portal:tk-100"}
                                  :unsourced? true}
           dispatcher)

    (exec! actor "t3-undercleared" {:op :ticket/route :subject "tk-200" :ticket-id "tk-200"
                                     :technician-id "tech-100" :hours 2.0
                                     :source {:class :monitoring-system-alert :ref "alert:tk-200"}}
           dispatcher)

    (exec! actor "t4-uncertified" {:op :ticket/route :subject "tk-300" :ticket-id "tk-300"
                                    :technician-id "tech-250" :hours 3.0
                                    :source {:class :monitoring-system-alert :ref "alert:tk-300"}}
           dispatcher)

    (exec! actor "t5-sla-urgent" {:op :ticket/route :subject "tk-300" :ticket-id "tk-300"
                                   :technician-id "tech-300" :hours 2.0
                                   :source {:class :monitoring-system-alert :ref "alert:tk-300"}}
           dispatcher)
    (approve! actor "t5-sla-urgent")

    (exec! actor "t6-dispute" {:op :dispute/request :subject "tk-100"
                                :disputed-field :hours :claim 3.0}
           manager)
    (approve! actor "t6-dispute")
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger ticket-id]
  (last (filter #(= (:subject %) ticket-id) ledger)))

(defn- status-cell [ledger ticket-id]
  (let [f (last-fact-for ledger ticket-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :policy-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- ticket-row [ledger {:keys [id client category required-access-tier sla-remaining-minutes]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc client) (esc (name (or category :n-a)))
          (esc (name (or required-access-tier :n-a)))
          (esc sla-remaining-minutes) (status-cell ledger id)))

(defn- ledger-row [{:keys [t op actor subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc actor) (esc subject)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract (README `Run`
  ;; section, `itsupport.policy`/`itsupport.phase`) -- documentation of
  ;; fixed behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:ticket/route</code></td><td><span class=\"ok\">auto-commit in phase 3 when governor-clean (RBAC &middot; access-tier &middot; security-incident cert &middot; source provenance all pass)</span> &middot; <span class=\"warn\">SLA-breach-imminent (&lt;60min remaining) ALWAYS escalates regardless of confidence</span></td></tr>"
   "        <tr><td><code>:disclosure/query</code></td><td><span class=\"err\">HARD hold if contract inactive/unknown tenant, or requested columns exceed the contract's tier</span></td></tr>"
   "        <tr><td><code>:dispute/request</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto-applies, any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        tickets (->> (store/all-tickets db)
                     (filter #(#{"tk-100" "tk-200" "tk-300"} (:id %)))
                     (sort-by :id))
        ticket-rows (str/join "\n" (map (partial ticket-row ledger) tickets))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-6209 &middot; itsupport.render-html &middot; IT managed-services ticket routing</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>IT managed-services / helpdesk ticket routing (ISIC 6209) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · SLA-urgent/dispute always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Tickets</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>itsupport.store</code> via <code>itsupport.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Ticket</th><th>Client</th><th>Category</th><th>Required tier</th><th>SLA remaining (min)</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     ticket-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (TicketGovernor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden by a human approver. Security-incident tickets require a real, named incident-response certification; SLA-urgent routing and any dispute always reach a human.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Actor</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts )")))
