(ns farm-advisory.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [farm-advisory.store :as store]
            [farm-advisory.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Alice Farm" :verified? true})
    (store/register-site! st {:site-id "site-1" :client-id "client-1" :location "North Field" :registered? true})
    st))

(deftest ok-on-clean-advisory-report
  (let [st (fresh-store)
        proposal {:op :draft-advisory-report :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:client-id "client-1" :site-id "site-1"} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        proposal {:op :draft-advisory-report :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:client-id "no-such-client" :site-id "site-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-unverified-client
  (let [st (store/mem-store)
        _ (store/register-client! st {:client-id "client-2" :name "Unverified Farm" :verified? false})
        _ (store/register-site! st {:site-id "site-2" :client-id "client-2" :location "Field" :registered? true})
        proposal {:op :draft-advisory-report :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:client-id "client-2" :site-id "site-2"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-unregistered-site
  (let [st (fresh-store)
        proposal {:op :draft-advisory-report :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:client-id "client-1" :site-id "no-such-site"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-site (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        proposal {:op :draft-advisory-report :effect :direct-write :confidence 0.9 :stake :low}
        v (governor/check {:client-id "client-1" :site-id "site-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest escalates-on-pest-disease-flag
  (let [st (fresh-store)
        proposal {:op :flag-pest-disease-risk :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:client-id "client-1" :site-id "site-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest escalates-on-high-cost-supply-order
  (let [st (fresh-store)
        proposal {:op :order-supplies :effect :propose :confidence 0.9 :stake :medium}
        v (governor/check {:client-id "client-1" :site-id "site-1" :cost 7500} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest ok-on-low-cost-supply-order
  (let [st (fresh-store)
        proposal {:op :order-supplies :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:client-id "client-1" :site-id "site-1" :cost 1000} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest escalates-on-low-confidence
  (let [st (fresh-store)
        proposal {:op :log-site-assessment :effect :propose :confidence 0.2 :stake :low}
        v (governor/check {:client-id "client-1" :site-id "site-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest store-records-and-ledger-append-only
  (let [st (fresh-store)]
    (store/commit-record! st {:site-id "site-1" :op :log-site-assessment})
    (store/append-ledger! st {:disposition :commit})
    (is (= 1 (count (store/records-of st "site-1"))))
    (is (= 1 (count (store/ledger st))))))
