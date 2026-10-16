# Conventions

Rules that **every** service in this repo follows. Facts about one service go in that service's `README.md`.
If a service has to break a rule here, write an ADR in `guide/adr/` and link it from the service's README. Don't create a local conventions file.

Sections marked *(from phase NN)* apply once that part of the roadmap is built.

## 1. Naming

| Thing | Rule | Example |
|---|---|---|
| Service folder / Gradle project | kebab-case, ends in `-service` | `static-data-service`, `trade-service` |
| Java base package | `com.ftb.<service name without -service, no hyphens>` | `com.ftb.staticdata`, `com.ftb.marketdata` |
| Classes | PascalCase, no abbreviations except domain terms (`Fx`, `Ssi`, `Utc`) | `TradeService`, `SettlementInstruction` |
| Database | `<short name>db` | `staticdb`, `tradedb` |
| Tables and columns | snake_case, tables plural | `trades`, `trade_versions`, `value_date` |
| Kafka topics *(from phase 03)* | `<domain>.events`, dead letters `<topic>.DLT` | `trade.events`, `trade.events.DLT` |
| Event types *(from phase 03)* | UPPER_SNAKE, past tense | `TRADE_BOOKED`, `TRADE_CANCELLED` |

Domain words come from `CONTEXT.md`. Use the same word in code, endpoints, events and DB columns: `book` / `amend` / `validate` / `cancel`, never synonyms.

## 2. Ports

| Port | Service |
|---|---|
| 5001 | static-data-service |
| 5002 | trade-service |
| 5003 | position-service |
| 5004 | market-data-service |
| 5005 | risk-service |
| 5006 | settlement-service |
| 5007 | reporting-service |
| 8080 | api-gateway |
| 9092 | Kafka |

- `server.port` in `application.yaml`, `EXPOSE` in the Dockerfile and the compose mapping use the **same** number.
- Postgres ports are **not** published to the host (host 5432 is used by another project). If a local DB tool needs access, publish on 5433 and up.

## 3. Code layout

```
com.ftb.<service>
├─ controller   REST endpoints only: map HTTP ↔ DTO, no business rules
├─ dto          request / response records
├─ domain       aggregates, value objects, domain events, business rules (the centre, see §4)
├─ repository   Spring Data interfaces, one per aggregate root
├─ service      use cases: load → call domain → save, transactions
├─ client       calls to other services, one class per dependency (anti-corruption layer)
└─ config       Spring configuration
```

- Lifecycle rules live **in the entity** (`trade.validate()`, `trade.amend()`), never as setters called from a controller or service.
- Never return a JPA entity from a controller. Always map to a DTO.

## 4. Domain model (DDD)

Each service is one **bounded context** with its own model and language. The context map and glossary are in `CONTEXT.md`.

### The domain is the centre

- Business rules live in `domain/`. The `service` package only orchestrates: **load → call a domain method → save → record events**.
- A `service` method with `if` statements about business rules is a smell. Move the rule into the aggregate or a domain service.
- Domain classes don't depend on controllers, DTOs, clients or Kafka.
- Pragmatic exception: JPA entities **are** the domain model, so JPA annotations are allowed in `domain/`. The business rules must still live in those classes, not around them.

### Value objects

Small immutable types that validate themselves on creation. Prefer them to loose primitives.

| Instead of | Use | Guarantees |
|---|---|---|
| `BigDecimal amount` + `String currency` | `Money` | amount always has a currency; can't add EUR to USD |
| `String pair` | `CurrencyPair` | 6 letters, base ≠ quote, market convention |
| `BigDecimal rate` | `Rate` | positive, scale 8 |
| `Long id` / `String code` | `TradeId`, `PortfolioCode`, `CounterpartyCode` | ids of different things can't be swapped |

- Write them as Java `record`s with validation in the compact constructor.
- Map them to columns with JPA `@Embeddable` or an `AttributeConverter`.
- Value objects compare by value, have no id, and are never modified. Operations return a new instance (`money.plus(other)`).

```java
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN);
    }

    public Money plus(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add " + other.currency + " to " + currency);
        }
        return new Money(amount.add(other.amount), currency);
    }
}
```

### Aggregates

An **aggregate** is a cluster of objects changed together as one unit, guarded by its **root** (e.g. `Trade` with its `TradeVersion` history).

- **Change an aggregate only through its root.** Nothing outside `Trade` modifies a `TradeVersion`.
- **One repository per aggregate root.** There is a `TradeRepository`, but no `TradeVersionRepository`.
- **One aggregate per transaction.** If a use case changes two aggregates, the second one changes later, through an event.
- **Reference other aggregates by id or code**, never by object reference: `Trade` holds a `PortfolioCode`, not a `Portfolio`.
- Keep aggregates small. Add something to an aggregate only if a rule needs it to change in the same transaction.
- Invariants are checked inside the root's methods, so an aggregate is never saved in an invalid state.

### Domain events vs integration events *(from phase 03)*

| | Domain event | Integration event |
|---|---|---|
| Lives | Inside one service | On Kafka, between services |
| Audience | Code in the same bounded context | Other bounded contexts |
| Can change | Freely | Only by adding fields or a new `eventVersion` |
| Example | `TradeAmended` (Java record) | `TRADE_AMENDED` with the envelope from §8 |

- Aggregates **record** domain events. They never publish to Kafka themselves.
- The outbox **maps** domain events to integration events. This keeps the public contract stable while the internal model evolves.

### Anti-corruption layer

- A call to another service goes through the `client` package, which **translates** the other service's response into this service's own types.
- The rest of the code never uses another service's DTOs. Example: trade-service's `StaticDataClient` returns a `LocalDate spotDate` or a `TradingPermission`, not static-data-service's JSON objects.
- If the other service changes its API, only the client class changes.

## 5. REST API

- Resources are plural nouns: `GET /api/trades`, `GET /api/trades/{id}`.
- Actions that aren't plain CRUD are `POST` sub-resources named with the glossary verb: `POST /api/trades/{id}/validate`.
- Errors use Spring's `ProblemDetail` (RFC 9457) with an extra stable `errorCode` field:

```json
{ "type": "about:blank", "title": "Invalid transition", "status": 409,
  "detail": "Trade 42 cannot be validated from status CANCELLED",
  "errorCode": "TRADE_INVALID_TRANSITION" }
```

| Status | When |
|---|---|
| 400 | Request validation failed |
| 403 | Not allowed (entitlements, maker-checker) |
| 404 | Resource not found |
| 409 | Invalid state transition, optimistic-lock conflict |
| 422 | Valid request but a business rule refuses it (e.g. limit breach) |

## 6. Money, rates and dates

- Money and rates are **`BigDecimal`**, never `double` or `float`.
- An amount is never stored or sent without its currency: always `(amount, currency)` together, in DB columns and JSON. In code, use the `Money` value object (§4).
- Amounts use the currency's decimals (JPY and VND 0, most others 2). Rates use 8 decimals.
- Rounding mode is `RoundingMode.HALF_EVEN`, set explicitly every time scale changes.
- `LocalDate` for business dates (trade date, value date). `Instant` for timestamps, stored in UTC.
- Never call `LocalDate.now()` / `Instant.now()` in business code. Inject a `java.time.Clock`.
- *(from phase 08)* The **business date** comes from one source. No service decides "today" on its own.

## 7. Database

- One database per service. A service never reads another service's tables.
- Schema changes only through **Flyway**: `src/main/resources/db/migration/V<n>__<description>.sql`, e.g. `V1__create_counterparties.sql`.
- Never edit a migration that has already run. Add a new one.
- `spring.jpa.hibernate.ddl-auto: validate`. Never `update` or `create`.
- Static data is never hard-deleted: use a `status` column (`ACTIVE` / `INACTIVE`).
- Other services refer to static data by its **natural code** (`EUR`, `FX-SPOT-LDN`), not by database id.
- Trades are never overwritten: every amend writes a history row.

## 8. Events *(from phase 03)*

- Every event uses the same envelope: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `aggregateId`, `payload`.
- The Kafka message key is the aggregate id (e.g. `tradeId`), so events of one trade stay in order.
- The payload carries the **full snapshot** and its version, not just the changed fields.
- Publish through the transactional outbox. Never call `KafkaTemplate.send()` inside a DB transaction.
- Only **add** fields to a published event. To rename or remove one, publish a new `eventVersion`.
- Consumers are idempotent, ignore unknown fields, and can rebuild their state by replaying the topic.
- Every topic and event is documented in `guide/events.md`.

## 9. Testing

| Kind | What | Naming |
|---|---|---|
| Unit | Domain rules, calculations, calendars. Plain JUnit, no Spring context | `<Class>Test` |
| Integration | Spring context, real Postgres / Kafka via Testcontainers *(from phase 04)* | `<Class>IntegrationTest` |

- Test methods describe the behaviour in snake_case: `amend_bumps_version_and_resets_status_to_pending`.
- Finance numbers are checked against a spreadsheet, then pasted into `@ParameterizedTest` + `@CsvSource`.
- Every state machine has a test per allowed transition **and** per forbidden one.

## 10. Build (Gradle)

- One Gradle wrapper, at the repo root. Always run `.\gradlew` from the root. Upgrade it only with `.\gradlew wrapper --gradle-version <x.y>`.
- Every service is listed in `settings.gradle.kts`.
- **Versions live only in `gradle/libs.versions.toml`.** Libraries managed by Spring Boot are declared **without** a version.
- `gradle.properties` holds build-environment settings only: no versions, no secrets.
- Shared build setup lives in the `buildSrc` convention plugin `ftb.spring-service`. A service's `build.gradle.kts` holds only its plugin line and its own dependencies.
- Services never depend on each other's code: no `implementation(project(":other-service"))`. They talk only through REST and Kafka.
- One `.gitignore` and one `.gitattributes`, at the root.

## 11. Configuration and secrets

- Configuration in `src/main/resources/application.yaml`. Docker Compose overrides values with environment variables (`SPRING_DATASOURCE_URL`, …).
- Secrets are never committed. Local overrides go in `application-local.yaml` or `.env`, both git-ignored.

## 12. Git

### Commit messages: Conventional Commits

```
<type>(<scope>): <summary in imperative mood, lowercase, no period>
```

| Type | Use for | Example |
|---|---|---|
| `feat` | New behaviour | `feat(trade): add amend with version history` |
| `fix` | Bug fix | `fix(static-data): roll spot date over weekends` |
| `build` | Gradle, wrapper, dependencies | `build: add buildSrc convention plugin` |
| `ci` | GitHub Actions | `ci: run gradlew build on push` |
| `test` | Tests only | `test(trade): cover forbidden state transitions` |
| `refactor` | Code change, same behaviour | `refactor(position): extract contribution calculator` |
| `docs` | README, CONVENTIONS, CONTEXT, guide | `docs: add Gradle commands to README` |
| `chore` | Anything else | `chore: remove generated HELP.md` |

- **Scope** = the service short name (`static-data`, `trade`, `position`, …). Leave it out for repo-wide changes.
- **Imperative mood:** "add", "fix", "set up", not "added" or "fixes".
- **One logical change per commit.** If the summary needs "and", split it.

### Branches and tags

- One branch per phase: `part1/phase-01-static-data`. Merge to `main` when the phase's "done when" checklist passes.
- Tag the merge commit: `part1-phase01-done`.
- `gradlew` must stay executable in git (`100755`). After recreating it on Windows, run `git update-index --chmod=+x gradlew`.
