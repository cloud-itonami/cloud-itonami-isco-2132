(ns farm-advisory.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [farm-advisory.actor :as actor]
            [farm-advisory.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Alice Farm" :verified? true})
    (store/register-site! st {:site-id "site-1" :client-id "client-1" :location "North Field" :registered? true})
    st))

(deftest commits-a-clean-low-risk-advisory
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :site-id "site-1" :op :log-site-assessment :stake :low}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "site-1"))))))

(deftest holds-on-unregistered-client-without-committing
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "no-such-client" :site-id "site-1" :op :log-site-assessment :stake :low}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "site-1")))
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-then-commits-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; pest disease flag always escalates (governor invariant)
        request {:client-id "client-1" :site-id "site-1" :op :flag-pest-disease-risk :stake :high}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "site-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (some? (get-in resumed [:state :record])))
      (is (= 1 (count (store/records-of st "site-1")))))))

(deftest interrupts-high-cost-supply-recommendation
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :site-id "site-1" :op :order-supplies :stake :medium :cost 10000}
        interrupted (actor/run-request! graph request {} "thread-4")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "site-1")))
    (let [resumed (actor/approve! graph "thread-4")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "site-1")))))))
