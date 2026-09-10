# cloud-itonami-isco-2132

Open Occupation Blueprint for **ISCO-08 2132**: Farming, Forestry and Fisheries Advisers.

This repository designs a forkable OSS farming, forestry, and fisheries advisory support system: an adviser proposes recommendations and site assessments for a registered client's own decision and action, under a governor-gated actor that ensures all advice remains advisory (never direct actuation) and escalates risk.

## Advisory Premise

All cloud-itonami verticals are designed on the premise that a **human expert or robot performs
the domain work**. Here an agricultural adviser proposes recommendations, assessments, and risk flags for a client's registered farm or forest site, under an actor that proposes actions and an independent **Advisory Governor** that gates them. The governor never executes advice directly; `:high`/`:safety-critical` actions (such as pest/disease risk escalations or significant supply recommendations) require human sign-off from the client/adviser.

## Core Contract

```text
client registration + site registration + advisory history
        |
        v
Advisory Advisor -> Advisory Governor -> draft report, assess, recommend supplies, or human sign-off
        |
        v
client decision + advisory records + audit ledger
```

No automated advice can suppress a record or disclose sensitive data without governor approval and
audit evidence.

## Capability Layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `2132`). Required capabilities:

- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

## Reference Implementation (`:maturity :implemented`)

Full itonami Actor pattern (per ADR-2607011000 / CLAUDE.md's Actors
section): a real [`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph`, with the Advisor and Governor as distinct graph nodes and
human-in-the-loop interrupt/resume via checkpointing.

```text
:intake -> :advise -> :govern -> :decide -+-> :commit            (:ok? true)
                                           +-> :request-approval   (:escalate? true, interrupt-before)
                                           +-> :hold               (:hard? true)
```

- `src/farm_advisory/store.kotoba` — `Store` protocol + `MemStore`:
  registered clients, farm/forest sites, advisory records, an append-only audit ledger.
- `src/farm_advisory/advisor.kotoba` — `Advisor` protocol; `mock-advisor`
  (deterministic, default) proposes an advisory action from a
  request; `llm-advisor` wraps a `langchain.model/ChatModel` — either
  way the advisor only ever produces a `:propose`-effect proposal,
  never a committed record, and LLM parse failures always yield
  `:confidence 0.0` (forces escalation, never fabricated confidence).
- `src/farm_advisory/governor.kotoba` — `AdvisoryGovernor/check`: a pure
  function, wired as its own `:govern` node. Hard invariants
  (unregistered client, unregistered site, a proposal whose `:effect`
  isn't `:propose`) always route to `:hold`. Escalation invariants
  (`:flag-pest-disease-risk`, `:order-supplies` above cost threshold,
  or low advisor confidence) always route to `:request-approval` — an
  `interrupt-before` node that the graph checkpoints and only resumes
  on explicit human approval (`actor/approve!`), matching the premise
  that pest/disease risks and significant supply recommendations
  always require human sign-off.
- `src/farm_advisory/actor.kotoba` — `build-graph`, `run-request!`,
  `approve!`: the `langgraph.graph/state-graph` wiring itself.

```bash
clojure -M:test
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation).

## License

AGPL-3.0-or-later.
