(ns farm-advisory.store
  "SSoT for the ISCO-08 2132 farming, forestry and fisheries adviser actor.
  Store is a protocol injected into the `farm-advisory.actor` StateGraph — `MemStore`
  is the default, deterministic, zero-dep backend; a Datomic/kotoba-server-backed
  implementation can be swapped in without touching the actor or governor (itonami
  actor pattern, per ADR-2607011000 / CLAUDE.md Actors section).

  Domain:

    client — a registered agricultural/forestry client receiving advice
             (:client-id, :name, :verified?)
    site   — a farm/forest site managed by the client
             (:site-id, :client-id, :location, :registered?)
    record — a committed advisory record under a site (assessment,
             recommendation, risk flag, supply order) — written ONLY via
             commit-record!, never mutated in place
    ledger — an append-only audit trail of every proposal/verdict/
             disposition, regardless of outcome (commit or hold)")

(defprotocol Store
  (client-by-id [s client-id])
  (site-by-id [s site-id])
  (records-of [s site-id])
  (ledger [s])
  (register-client! [s client])
  (register-site! [s site])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client-by-id [_ client-id] (get-in @a [:clients client-id]))
  (site-by-id [_ site-id] (get-in @a [:sites site-id]))
  (records-of [_ site-id] (filter #(= site-id (:site-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-site! [s site]
    (swap! a assoc-in [:sites (:site-id site)] site) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :sites {} :records [] :ledger []} seed)))))
