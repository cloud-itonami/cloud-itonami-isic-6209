(ns itsupport.facts
  "R0 provenance/qualification catalog for the IT-support ticket-routing
  actor — the ONLY ticket-origin classes and technician-qualification
  standards the TicketGovernor will accept as grounds for a routing
  proposal (honesty over coverage, same discipline as sibling actors'
  facts catalogs).

  Two closed sets:
    1. `allowed-source-classes` — where a ticket's existence/severity claim
       came from (client-submitted, an automated monitoring alert, or an
       HR-verified certification record for a technician). A ticket or
       cert claim citing anything outside this set is rejected outright.
    2. `security-incident-certs` — real, named incident-response
       certifications; a technician must hold at least one to receive a
       `:category :security-incident` ticket.
    3. `access-tiers` — an ordered least-privilege scale (NIST SP 800-53
       AC-6 least-privilege framing, not a specific vendor cert) a
       technician's `:access-tier` must meet or exceed a ticket's
       `:required-access-tier`.")

(def allowed-source-classes
  #{:client-submitted-ticket :monitoring-system-alert :hr-verified-certification})

(def security-incident-certs
  "Real, named incident-response certifications. A technician holding at
  least one may receive a `:category :security-incident` ticket."
  #{:giac-gcih  ; GIAC Certified Incident Handler
    :cissp      ; (ISC)² Certified Information Systems Security Professional
    :chfi})     ; EC-Council Computer Hacking Forensic Investigator

(def access-tiers
  "Ordered least-privilege scale, low to high (NIST SP 800-53 AC-6 framing)."
  [:tier/standard :tier/elevated :tier/privileged])

(def ^:private tier-rank (into {} (map-indexed (fn [i t] [t i])) access-tiers))

(defn tier-at-least? [technician-tier required-tier]
  (>= (get tier-rank technician-tier -1) (get tier-rank required-tier 0)))

(defn class-allowed? [source-class]
  (contains? allowed-source-classes source-class))

(defn security-cert-allowed? [cert]
  (contains? security-incident-certs cert))

(defn coverage
  "Honest, machine-checkable report of what R0 actually covers."
  []
  {:source-classes allowed-source-classes
   :security-incident-cert-count (count security-incident-certs)
   :access-tier-count (count access-tiers)
   :note (str "R0 scope: 3 provenance classes, "
              (count security-incident-certs)
              " real named incident-response certifications, "
              (count access-tiers)
              "-level least-privilege access-tier scale. Extend only by "
              "appending a real, citable certification or a documented "
              "provenance class — never fabricate either.")})
