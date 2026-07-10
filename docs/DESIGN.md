# IT-Support Ticket-Routing Actor Design

TicketRouter-LLM を最下層ノードに封じ込め、TicketGovernor(独立系統)が
access-tier・security-incident 認定・SLA 緊急度を検閲する構図。
`cloud-itonami-isic-8299`(TaskRouter-LLM ⊣ RoutingGovernor)の写像。

## 1. なぜ actor 層が要るのか

チケットのトリアージ/ルーティングは LLM で加速できるが、**最終的な
ルーティング確定権限を持たせるのは危険**:

| LLM が起こしうる失敗 | 帰結 |
|---|---|
| elevated-access チケットを未達 tier の technician へ | least-privilege 違反 |
| security-incident チケットを無認定 technician へ | インシデント対応失敗 |
| SLA 逼迫チケットを高確信のまま自動処理 | エスカレーション漏れ |

## 2. OperationActor(`src/itsupport/operation.cljc`)

```
intake → advise → govern → decide ─┬─ commit
                                   ├─ escalate ─▶ request-approval → commit|hold
                                   └─ hold
```

## 3. TicketGovernor(`src/itsupport/policy.cljc`)

優先順位(HARD は人間承認でも上書き不可):

1. rbac
2. **access-tier-clearance-gate** — technician tier が ticket 要求 tier 未満なら拒否
3. **security-incident-misrouting-gate** — security-incident チケットは実在の認定保持者のみ
4. source-provenance-gate
5. licensed-disclosure
6. 確信度フロア(SOFT)
7. **sla-breach-imminent gate**(SOFT) — SLA まで60分未満は常に人間承認
8. dispute-request(SOFT、無条件)

## 4. SSoT(`src/itsupport/store.cljc`)

technicians(access-tier/certifications)・tickets(category/required-
access-tier/sla-remaining-minutes)・assignments・contracts・append-only
ledger。

## 5. R0(`src/itsupport/facts.cljc`)

出典クラス3種 + 実在認定3種(GIAC GCIH/CISSP/CHFI)+ 3段階 access-tier。

## 6. Phase 0→3(`src/itsupport/phase.cljc`)

`default-phase` = 1(保守的)。`dispute/request` はどの phase の `:auto`
にも入らない。
