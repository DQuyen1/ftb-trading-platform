# Context

The domain language of the platform. Use exactly these words in code, endpoints, events and database columns.
Coding rules are in `CONVENTIONS.md`. The full domain primer (with worked examples) is in `guide/roadmap-part1-fx-core.html`.

## 1. Bounded contexts

Each service is one bounded context: it owns its model, its words and its database.

| Context (service) | Department it mirrors | Owns | Status |
|---|---|---|---|
| **Static data** (`static-data-service`) | Static data team | legal entities, portfolios, traders, counterparties, SSIs, currencies, currency pairs, calendars | Part 1 · phase 01 |
| **Trading** (`trade-service`) | Front office | trades, their versions and lifecycle | phase 02 |
| **Positions** (`position-service`) | Front office | net positions, average rate, P&L | phase 04 |
| **Market data** (`market-data-service`) | Market data team | rate snapshots, closing rates | phase 05 |
| **Risk** (`risk-service`) | Middle office (risk control) | limits, limit usage, VaR | phase 06 |
| **Settlement** (`settlement-service`) | Back office | confirmations, settlement instructions, payments | phase 07 |
| **Reporting** (`reporting-service`) | Finance | datamart, daily P&L and risk reports | phase 08 |

## 2. Context map

Who depends on whom (upstream → downstream) and how they integrate.

```
                        ┌──────────────┐
                        │ Static data  │  upstream of everyone
                        └──────┬───────┘
                REST (ACL in each caller's client package)
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
   ┌────────────┐       ┌────────────┐       ┌────────────┐
   │  Trading   │──────►│    Risk    │       │ Settlement │
   └─────┬──────┘ REST  └────────────┘       └────────────┘
         │ pre-deal check        ▲                  ▲
         │                       │                  │
         └── trade.events ───────┼──────────────────┤
             (published          │                  │
              language)          ▼                  ▼
                          ┌────────────┐     ┌────────────┐
   ┌─────────────┐        │ Positions  │     │ Reporting  │
   │ Market data │─rates─►└────────────┘     └────────────┘
   └─────────────┘   (also to Risk)
```

| Relationship | Upstream | Downstream | Pattern | How |
|---|---|---|---|---|
| Reference data | Static data | Trading, Settlement, Risk | **Customer / Supplier** + **anti-corruption layer** | REST; each downstream translates responses in its `client` package |
| Trade feed | Trading | Positions, Risk, Settlement, Reporting | **Published language** (open host) | `trade.events` integration events, versioned envelope |
| Pre-deal check | Risk | Trading | **Customer / Supplier** | REST call with timeout + circuit breaker |
| Rates | Market data | Positions, Risk | **Published language** | `market.rates` events |
| Settlement status | Settlement | Trading, Reporting | **Published language** | `settlement.events` |

Downstream contexts **never** read an upstream context's database, and they keep their own copy of the data they need.

## 3. Same word, different meaning

| Word | In this context | Means |
|---|---|---|
| **Position** | Positions | Net amount per book and currency pair, with average rate and P&L |
| | Risk | Exposure used for limits and VaR (e.g. FX delta per currency) |
| **Trade** | Trading | The aggregate: full lifecycle, versions, maker-checker |
| | Settlement | Only the economics needed to pay: amounts, currencies, value date, counterparty |
| **Portfolio** | Static data | The definition: code, owning entity, parent in the tree, allowed traders |
| | Trading / Positions | Just a `PortfolioCode` reference |
| **Status** | Trading | `PENDING`, `VALIDATED`, `CANCELLED`, `SETTLED` |
| | Settlement | `CONFIRMED`, `MATCHED`, `SETTLED`, … (phase 07) |
| | Static data | `ACTIVE`, `INACTIVE` |

When code in one context talks about another context's concept, it uses its **own** type (e.g. settlement's `TradeEconomics`), never the other service's class.

## 4. Glossary

| Term | Meaning |
|---|---|
| **Amend** | Change a field of a live trade; creates a new version |
| **Average rate** | The rate an open position effectively cost |
| **Base currency** | First currency of a pair (EUR in EURUSD). Direction (buy / sell) always refers to it |
| **Book / portfolio** | Bucket a trade sits in; P&L, risk and limits are measured per book. Code example: `FX-SPOT-LDN` |
| **Business date** | The date being processed, separate from the clock |
| **Cancel** | Remove a trade that should never have existed (kept in history) |
| **Counterparty** | The other side of a trade |
| **Currency pair** | Two currencies in market convention order, e.g. `EURUSD`, `USDJPY` |
| **Legal entity / LEI** | The company that owns a trade / its 20-character global id |
| **Maker-checker** | The person who books or changes a trade can't validate it (4-eyes) |
| **Mid** | (bid + ask) ÷ 2; used for valuation |
| **MTM** | Mark-to-market: value at today's market prices (= unrealized P&L) |
| **Notional** | The trade's face amount, in the base currency |
| **P&L** | Profit and loss: realized (closed part) + unrealized (open part, MTM) |
| **Position** | See §3 |
| **Quote currency** | Second currency of a pair (USD in EURUSD) |
| **Spot date** | Value date of a spot trade: T+2 business days (USDCAD T+1) |
| **SSI** | Standard settlement instructions: where to pay a counterparty in a currency |
| **Tenor** | Period label relative to spot: `ON`, `TN`, `1W`, `1M`, `3M`, `1Y` |
| **Trade date** | Day the deal was agreed (T) |
| **Trader** | Person allowed to book trades in certain books |
| **Typology** | Deal classification: `FX_SPOT`, `FX_FORWARD` |
| **UTI** | Unique transaction identifier, shared with the counterparty for reporting |
| **Validate** | Second-person approval of a trade |
| **Value date** | Day the money moves |
| **Version** | Numbered state of a trade after each amend |

Add a term here **before** it appears in code.
