(ns farm-advisory.governor
  "AdvisoryGovernor — the independent safety/traceability layer for
  the ISCO-08 2132 farming, forestry and fisheries adviser actor.
  Wired as its own `:govern` node in `farm-advisory.actor`'s
  StateGraph, downstream of `:advise` — the Advisor has no notion of
  client/site provenance or risk, so this MUST be a separate system
  able to reject a proposal (itonami actor pattern, per ADR-2607011000 /
  CLAUDE.md Actors section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance      — the request's client must be registered
                                and verified.
    2. site provenance        — the request's site must be registered
                                and client-owned.
    3. no-actuation           — proposal :effect must be :propose.
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off, per
  the premise that pest/disease risks and significant supply
  recommendations always require human sign-off):
    4. :op :flag-pest-disease-risk (always escalates).
    5. :op :order-supplies with cost >= `supply-cost-threshold`.
    6. low confidence (< `confidence-floor`)."
  (:require [farm-advisory.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-threshold 5000)
(def ^:private escalating-ops #{:flag-pest-disease-risk})

(defn- hard-violations [{:keys [proposal]} client-record site-record]
  (cond-> []
    (or (nil? client-record) (not (:verified? client-record)))
    (conj {:rule :no-client :detail "unregistered or unverified client"})

    (or (nil? site-record) (not (:registered? site-record)))
    (conj {:rule :no-site :detail "unregistered site or site not owned by client"})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `farm-advisory.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [client-record (store/client-by-id store (:client-id request))
        site-record (store/site-by-id store (:site-id request))
        hard (hard-violations {:proposal proposal} client-record site-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        cost (get request :cost 0)
        high-cost-supply? (and (= :order-supplies (:op proposal))
                               (>= cost supply-cost-threshold))
        risky-op? (contains? escalating-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?) (not high-cost-supply?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op? high-cost-supply?))}))
