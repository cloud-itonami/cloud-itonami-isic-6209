(ns itsupport.phase
  "Phase 0→3 staged rollout. Where the TicketGovernor answers 'is this
  allowed?', the phase answers 'how much autonomy does the actor have
  *yet*?'. It can only ever make the actor MORE conservative than the
  governor.

    Phase 0  read-only        — no writes at all. `:disclosure/query` only
                                (still governor-gated).
    Phase 1  assisted-route   — `:ticket/route` allowed, every routing
                                needs human approval.
    Phase 2  + dispute        — adds `:dispute/request` (still
                                approval-only).
    Phase 3  supervised auto  — governor-clean, high-confidence
                                `:ticket/route` may auto-commit.

  `:dispute/request` is deliberately NEVER a member of any phase's `:auto`
  set, at any phase.")

(def read-ops  #{:disclosure/query})
(def write-ops #{:ticket/route :dispute/request})

(def phases
  {0 {:label "read-only"      :writes #{}
                               :auto #{}}
   1 {:label "assisted-route" :writes #{:ticket/route}
                               :auto #{}}
   2 {:label "assisted-dispute" :writes #{:ticket/route :dispute/request}
                                 :auto #{}}
   3 {:label "supervised-auto"  :writes #{:ticket/route :dispute/request}
                                 :auto #{:ticket/route}}})

(def default-phase
  "The phase used when `context` carries no :phase at all. This is
  directly reachable by any ordinary caller that simply omits :phase --
  not just malformed/malicious input -- so it must be the MOST
  CONSERVATIVE phase, never the most permissive (the same fail-open bug
  found and fixed this session across cloud-itonami-isic-6311's sibling
  templates)."
  1)

(defn gate
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)      {:disposition :hold :reason nil}
      (contains? read-ops op)             {:disposition governor-disposition :reason nil}
      (not (contains? writes op))         {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))     {:disposition :escalate :reason :phase-approval}
      :else                               {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
