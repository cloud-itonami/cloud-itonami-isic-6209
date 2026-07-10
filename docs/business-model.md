# Open Business Blueprint: cloud-itonami-isic-6209

This repository publishes an OSS business model for operating an IT
managed-services/helpdesk ticket-routing service on itonami.cloud.

## Classification

- Repository name: `cloud-itonami-isic-6209`
- Primary classification: ISIC Rev.4 6209 (Other information technology
  and computer service activities), narrowed to managed-services/helpdesk
  ticket routing specifically
- Served domain: incident triage/routing to contracted technicians by
  access-tier and certification, governed disclosure, dispute handling —
  never payroll, never employer-of-record

## Customer

- SMBs/mid-market IT departments needing a governed, audit-ready helpdesk
  without building routing/compliance logic themselves
- MSPs (managed service providers) needing an OSS routing layer over their
  own contracted technician pool
- other `cloud-itonami-{ISIC}` blueprint operators needing IT-support
  dispatch as a licensed capability

## Problem

Helpdesk platforms (Zendesk, Freshdesk) route tickets by simple rules or
LLM heuristics with no structural guarantee against routing a
security-incident ticket to an unqualified technician, or an
elevated-access ticket to someone below the required clearance tier.

## Offer

- technician registry (access-tier, certifications)
- ticket triage/routing, governed by access-tier and certification checks
- SLA-urgency awareness (near-breach tickets always reach a human)
- governed, tier-scoped disclosure
- a dispute/reassignment channel, always human-reviewed
- immutable audit ledger

## Revenue

- per-technician or per-ticket subscription (contract tenant × tier)
- tiered subscriptions: `:tier/basic` (status only) → `:tier/pro`
  (+ assigned technician) → `:tier/institutional` (+ raw provenance)
- wholesale API access to other cloud-itonami blueprint operators

## Non-Negotiables

- Do not commit real client tickets or real technician PII.
- Do not add a schema field for payroll, timesheet approval, or
  employer-of-record status.
- Do not bypass the TicketGovernor for production routing or disclosures.
- Do not fabricate a certification-catalog entry.
