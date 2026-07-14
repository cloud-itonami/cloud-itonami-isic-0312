(ns freshwater-fishing.sim
  "Simulation/demo: drive the freshwater fishing fleet operations actor
  through a few scenarios."
  (:require [freshwater-fishing.store :as store]
            [freshwater-fishing.operation :as operation]))

(defn -main []
  (println "Freshwater Fishing Fleet Operations Coordination Actor - Demo")
  (println "===============================================================")

  ;; Setup: create store with a test vessel.
  (let [test-vessel {:vessel-id "freshwater-vessel-001"
                     :name "River Hope"
                     :status :active
                     :water-body "Lake Biwa"
                     :permit-number "PERMIT-INLAND-001-2026"
                     :quota-kg 8000
                     :landed-kg 0}
        st (store/mem-store {:fixtures {"freshwater-vessel-001" test-vessel}})

        ;; Build the operation actor.
        actor (operation/build st)]

    (println "\nVessel registered:")
    (println "  Vessel ID:" (:vessel-id test-vessel))
    (println "  Name:" (:name test-vessel))
    (println "  Water body:" (:water-body test-vessel))
    (println "  Permit:" (:permit-number test-vessel))
    (println "  Quota (kg):" (:quota-kg test-vessel))

    (println "\nOperation Actor built and ready.")
    (println "Governance rules active:")
    (println "  - Vessel/permit verification required")
    (println "  - All effects :propose only")
    (println "  - Safety concerns always escalate immediately")
    (println "  - High-cost supply orders escalate")
    (println "  - Quota-exceedance catch records are hard-blocked (permanent)")
    (println "  - Navigation / vessel-command / catch decisions are blocked (vessel operator's exclusive authority)")))
