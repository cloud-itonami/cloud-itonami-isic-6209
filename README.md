# cloud-itonami-isic-6209

Open Business Blueprint for **ISIC Rev.4 6209**: other information
technology and computer service activities, narrowed to an **IT
managed-services / helpdesk ticket-routing** service — the Datadog-on-call
/ Zendesk-with-technicians class of business — published as an OSS
business that any qualified operator can fork, deploy, run, improve and
sell.

Client incidents arrive as tickets and get triaged/routed to a pool of
contracted technicians by required access-tier and, for security
incidents, real named incident-response certification. Built on this
workspace's [`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime — the same actor pattern as
[`cloud-itonami-isic-8299`](https://github.com/cloud-itonami/cloud-itonami-isic-8299)
and [`cloud-itonami-isic-6311`](https://github.com/cloud-itonami/cloud-itonami-isic-6311).

> **Why an actor layer at all?** A TicketRouter-LLM is great at
> normalizing incoming tickets and drafting routing proposals — but it
> has **no notion of least-privilege access rules, incident-response
> certification requirements, or SLA urgency**. Letting it route directly
> invites an elevated-access ticket reaching an under-cleared technician
> (a real least-privilege violation), a security-incident ticket reaching
> someone with no incident-response credential, or a near-SLA-breach
> ticket auto-processing without anyone noticing. This project seals the
> TicketRouter-LLM into a single node and wraps it with an independent
> **TicketGovernor**, a human **review workflow**, and an immutable
> **audit ledger**.

## Scope (deliberately narrow — read this before anything else)

This actor **routes tickets to already-contracted technicians**. It never
extends employer-of-record labor responsibility (distinct from
[`cloud-itonami-isic-7820`](https://github.com/cloud-itonami/cloud-itonami-isic-7820)'s
dispatch model), never processes payroll, never approves timesheets.
Security-incident tickets require a real, named certification from a
closed R0 catalog (`src/itsupport/facts.cljk`: GIAC GCIH, CISSP, CHFI) —
never a bare "the LLM decided this technician is qualified".

## The core contract

```
request + injected role/tenant/phase context
        │
        ▼
   ┌─────────────────┐  proposal      ┌─────────────────────┐
   │ TicketRouter-LLM │ ─────────────▶ │ TicketGovernor       │  (independent system)
   │ (sealed)         │  draft +       │  access-tier ·       │
   └─────────────────┘  source         │  incident-cert · SLA │
                                        └─────────────────────┘
                                              │
                                   commit / disclose only if allowed
                                              ▼
                                    append-only audit ledger
```

**Single invariant**: TicketRouter-LLM never routes, discloses, or
resolves a dispute the TicketGovernor would reject.

## Run

```bash
kbb -M:dev:test   # governor contract · store parity · phases · facts
kbb -M:dev:run    # 8-operation demo through one OperationActor
kbb -M:lint
```

## Non-Negotiables

- Do not commit real client tickets or real technician PII.
- Do not add a schema field for payroll, timesheet approval, or
  employer-of-record status.
- Do not bypass the TicketGovernor for production routing or disclosures.
- Do not serve a disclosure without an active, registered contract.
- Do not fabricate a certification-catalog entry.

License: AGPL-3.0-or-later.
