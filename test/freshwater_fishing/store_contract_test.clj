(ns freshwater-fishing.store-contract-test
  "Contract tests for the freshwater fishing fleet Store."
  (:require [clojure.test :refer [deftest is]]
            [freshwater-fishing.store :as store]))

(deftest vessel-lookup
  (let [v {:vessel-id "vessel-001" :name "Test Vessel" :status :active}
        st (store/mem-store {:fixtures {"vessel-001" v}})]
    (is (= v (store/vessel st "vessel-001")))
    (is (nil? (store/vessel st "nonexistent")))))

(deftest log-catch-record
  (let [st (store/mem-store {:fixtures {"vessel-001" {:vessel-id "vessel-001" :status :active}}})
        record {:species "rainbow-trout" :quantity-kg 45}
        st2 (store/log-catch! st "vessel-001" record)]
    (is (contains? (store/vessel st2 "vessel-001") :last-catch-record))
    (is (= (:species (get-in (store/vessel st2 "vessel-001") [:last-catch-record]))
           "rainbow-trout"))))

(deftest log-catch-record-accrues-quota
  (let [st (store/mem-store {:fixtures {"vessel-001" {:vessel-id "vessel-001" :status :active
                                                        :quota-kg 500 :landed-kg 0}}})
        st1 (store/log-catch! st "vessel-001" {:species "carp" :quantity-kg 80})
        st2 (store/log-catch! st1 "vessel-001" {:species "carp" :quantity-kg 20})]
    (is (= 100 (:landed-kg (store/vessel st2 "vessel-001"))))))

(deftest audit-trail
  (let [st (store/mem-store {:fixtures {"vessel-001" {:vessel-id "vessel-001" :status :active}}})
        st1 (store/log-catch! st "vessel-001" {:quantity-kg 100})
        st2 (store/flag-safety-concern! st1 "vessel-001" {:concern "rising-water-level"})
        log (store/audit-log st2 "vessel-001")]
    (is (= 2 (count log)))
    (is (some #(= :catch-logged (:type %)) log))
    (is (some #(= :safety-concern-flagged (:type %)) log))))

(deftest schedule-maintenance
  (let [st (store/mem-store {:fixtures {"vessel-001" {:vessel-id "vessel-001" :status :active}}})
        maint {:type :outboard-motor-service :scheduled-date "2026-08-20"}
        st2 (store/schedule-maintenance! st "vessel-001" maint)]
    (is (contains? (get-in (store/vessel st2 "vessel-001") [:pending-maintenance 0])
                   :type))
    (is (= :outboard-motor-service (get-in (store/vessel st2 "vessel-001") [:pending-maintenance 0 :type])))))

(deftest order-supply
  (let [st (store/mem-store {:fixtures {"vessel-001" {:vessel-id "vessel-001" :status :active}}})
        order {:item "fuel" :quantity-l 200 :estimated-cost 600}
        st2 (store/order-supply! st "vessel-001" order)]
    (is (contains? (get-in (store/vessel st2 "vessel-001") [:supply-orders 0])
                   :item))
    (is (= 600 (get-in (store/vessel st2 "vessel-001") [:supply-orders 0 :estimated-cost])))))
