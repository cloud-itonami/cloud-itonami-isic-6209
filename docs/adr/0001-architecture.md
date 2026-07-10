# ADR-0001: cloud-itonami-isic-6209 — TicketRouter-LLM を封じ込めた知能ノードとする IT サポート・チケットルーティング actor 設計

- Status: Accepted (2026-07-10)
- 関連: `cloud-itonami-isic-8299`(TaskRouter-LLM ⊣ RoutingGovernor、直接の
  手本)、`cloud-itonami-isic-6311`(フリート標準パターン)

## 課題

ISIC Rev.4 6209「Other information technology and computer service
activities」は n.e.c. の広いコードであり、単純な relabeling を避けるため
**IT managed-services/helpdesk のチケットルーティング**に narrow した。
LLM にルーティング確定を直接行わせると、least-privilege 違反・
インシデント対応失敗・SLA エスカレーション漏れのリスクがある。

## 決定

TicketRouter-LLM は proposal のみを返す助言者とし、独立した
TicketGovernor がすべてのルーティング・開示・紛争解決を検閲する。
**単一不変条件**: TicketRouter-LLM は、TicketGovernor が拒否する
ルーティング確定・開示・紛争解決を決して行わない。

domain-unique HARD チェック2つ: `access-tier-clearance-gate`(NIST SP
800-53 AC-6 least-privilege 準拠の3段階 tier)、
`security-incident-misrouting-gate`(GIAC GCIH/CISSP/CHFI の実在認定
カタログ)。`sla-breach-imminent gate` は SOFT(常時 escalate)。

`default-phase` = 1(保守的)を実装当初から採用(isic-6311 系列で発見
された fail-open バグを事前に回避)。

## Consequences

- (+) `kotoba-lang/industry` registry 6209 スロットが実装へ昇格。
- (+) narrowing 判断を明記(n.e.c. コードの安易な relabeling を回避)。
- (-) R0 認定カタログは3種のみ。

## 代替案と不採用理由

- **narrowing せず汎用「IT support」actor として実装**: スコープが際限
  なく広がる。`cloud-itonami-isic-6311`/`isic-8299` が確立した narrowing
  規律に従い、具体的な1業態(managed-services/helpdesk)に絞った。
