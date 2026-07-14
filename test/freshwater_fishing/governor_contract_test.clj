(ns freshwater-fishing.governor-contract-test
  "Contract tests for the FreshwaterFishingOperationsGovernor."
  (:require [clojure.test :refer [deftest is]]
            [freshwater-fishing.governor :as governor]
            [freshwater-fishing.store :as store]))

(deftest effect-mismatch-detection
  (let [st (store/mem-store)
        req {:op :log-catch-record :vessel-id "vessel-001"}
        prop {:effect :commit}]
    (is (some #(= :effect-mismatch (:rule %))
              (:violations (governor/check req nil prop st))))))

(deftest vessel-verification-required
  (let [st (store/mem-store)
        req {:op :log-catch-record :vessel-id "nonexistent"}
        prop {:effect :propose :value {}}]
    (is (some #(= :vessel-not-found (:rule %))
              (:violations (governor/check req nil prop st))))))

(deftest vessel-must-be-active
  (let [inactive-vessel {:vessel-id "vessel-001" :status :inactive}
        st (store/mem-store {:fixtures {"vessel-001" inactive-vessel}})
        req {:op :log-catch-record :vessel-id "vessel-001"}
        prop {:effect :propose :value {}}]
    (is (some #(= :vessel-inactive (:rule %))
              (:violations (governor/check req nil prop st))))))

(deftest only-propose-effects-allowed
  (let [active-vessel {:vessel-id "vessel-001" :status :active}
        st (store/mem-store {:fixtures {"vessel-001" active-vessel}})
        req {:op :log-catch-record :vessel-id "vessel-001"}
        prop {:effect :commit :value {}}]
    (is (some #(= :non-propose-effect (:rule %))
              (:violations (governor/check req nil prop st))))))

(deftest navigation-command-blocked
  (let [active-vessel {:vessel-id "vessel-001" :status :active}
        st (store/mem-store {:fixtures {"vessel-001" active-vessel}})
        req {:op :navigate-vessel :vessel-id "vessel-001"}
        prop {:effect :propose :value {:heading 90}}]
    (is (some #(= :blocked-operation (:rule %))
              (:violations (governor/check req nil prop st))))))

(deftest vessel-command-blocked
  (let [active-vessel {:vessel-id "vessel-001" :status :active}
        st (store/mem-store {:fixtures {"vessel-001" active-vessel}})
        req {:op :command-vessel :vessel-id "vessel-001"}
        prop {:effect :propose :value {:throttle 80}}]
    (is (some #(= :blocked-operation (:rule %))
              (:violations (governor/check req nil prop st))))))

(deftest catch-decision-blocked
  (let [active-vessel {:vessel-id "vessel-001" :status :active}
        st (store/mem-store {:fixtures {"vessel-001" active-vessel}})
        req {:op :decide-catch :vessel-id "vessel-001"}
        prop {:effect :propose :value {:target-species "trout"}}]
    (is (some #(= :blocked-operation (:rule %))
              (:violations (governor/check req nil prop st))))))

(deftest forbidden-fields-blocked
  (let [active-vessel {:vessel-id "vessel-001" :status :active}
        st (store/mem-store {:fixtures {"vessel-001" active-vessel}})
        req {:op :log-catch-record :vessel-id "vessel-001"}
        prop {:effect :propose :value {:vessel-id "vessel-002"}}]
    (is (some #(= :forbidden-field (:rule %))
              (:violations (governor/check req nil prop st))))))

(deftest quota-exceedance-hard-blocked
  (let [active-vessel {:vessel-id "vessel-001" :status :active
                        :quota-kg 1000 :landed-kg 900}
        st (store/mem-store {:fixtures {"vessel-001" active-vessel}})
        req {:op :log-catch-record :vessel-id "vessel-001"}
        prop {:effect :propose :value {:quantity-kg 500} :confidence 0.95}
        verdict (governor/check req nil prop st)]
    (is (:hard? verdict))
    (is (some #(= :quota-exceedance (:rule %)) (:violations verdict)))))

(deftest quota-within-limit-not-blocked
  (let [active-vessel {:vessel-id "vessel-001" :status :active
                        :quota-kg 1000 :landed-kg 200}
        st (store/mem-store {:fixtures {"vessel-001" active-vessel}})
        req {:op :log-catch-record :vessel-id "vessel-001"}
        prop {:effect :propose :value {:quantity-kg 300} :confidence 0.95}
        verdict (governor/check req nil prop st)]
    (is (not (some #(= :quota-exceedance (:rule %)) (:violations verdict))))))

(deftest safety-concern-always-escalates
  (let [active-vessel {:vessel-id "vessel-001" :status :active}
        st (store/mem-store {:fixtures {"vessel-001" active-vessel}})
        req {:op :flag-safety-concern :vessel-id "vessel-001"}
        prop {:effect :propose :value {:concern "rising-water-level"}}
        verdict (governor/check req nil prop st)]
    (is (:escalate? verdict))
    (is (some #(= :safety-concern-escalation (:rule %)) (:soft-flags verdict)))))

(deftest high-cost-supply-escalates
  (let [active-vessel {:vessel-id "vessel-001" :status :active}
        st (store/mem-store {:fixtures {"vessel-001" active-vessel}})
        req {:op :order-supplies :vessel-id "vessel-001"}
        prop {:effect :propose :value {:item "fuel" :estimated-cost 15000}}
        verdict (governor/check req nil prop st)]
    (is (:escalate? verdict))
    (is (some #(= :high-cost-escalation (:rule %)) (:soft-flags verdict)))))

(deftest low-confidence-escalates
  (let [active-vessel {:vessel-id "vessel-001" :status :active}
        st (store/mem-store {:fixtures {"vessel-001" active-vessel}})
        req {:op :log-catch-record :vessel-id "vessel-001"}
        prop {:effect :propose :value {} :confidence 0.4}
        verdict (governor/check req nil prop st)]
    (is (:escalate? verdict))))

(deftest clean-proposal-passes
  (let [active-vessel {:vessel-id "vessel-001" :status :active
                        :quota-kg 8000 :landed-kg 0}
        st (store/mem-store {:fixtures {"vessel-001" active-vessel}})
        req {:op :log-catch-record :vessel-id "vessel-001"}
        prop {:effect :propose :value {:quantity-kg 500} :confidence 0.95}
        verdict (governor/check req nil prop st)]
    (is (:ok? verdict))
    (is (not (:hard? verdict)))
    (is (not (:escalate? verdict)))))
