# Operator Guide

## 1. Fork and Run

```bash
git clone https://github.com/cloud-itonami/cloud-itonami-isic-6209
cd cloud-itonami-isic-6209
clojure -M:dev:test
clojure -M:dev:run
```

## 2. Production Checklist

- replace demo technicians/tickets with real, source-cited data
- configure Datomic Local, kotoba-server or an equivalent durable SSoT
- configure the LLM adapter through environment variables or secret manager
- define subscriber contract tenants/tiers and RBAC rules
- run `clojure -M:dev:test` / `clojure -M:lint`
- verify audit-ledger export
- document backup/restore and incident response
- get written legal review on data-handling for the jurisdictions you serve

## 3. Operator Responsibilities

- verify technician certification claims against real issuing bodies
  before registering them in the store
- secure infrastructure and tenant isolation
- human review workflow for SLA-urgent and dispute-request operations
- data-retention policy
- security updates

The OSS project provides software and an operating blueprint. It does not
verify technician credentials on the operator's behalf.
