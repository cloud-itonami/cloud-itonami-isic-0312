(ns freshwater-fishing.store
  "Store protocol and in-memory implementation for freshwater (river/lake/
  inland-waters) fishing fleet operations coordination. Separates data
  persistence from business logic, enabling swaps (in-mem ->
  Datomic/kotoba-server)."
  #?(:clj (:refer-clojure :exclude [type])))

(defprotocol Store
  "Persistent store for vessel/permit records, catch history, and fleet
  operations logs."

  (vessel [st vessel-id]
    "Returns vessel record by :vessel-id, or nil if not found.
     Vessel record: {:vessel-id .. :name .. :status :active|:inactive
     :water-body .. :permit-number .. :quota-kg .. :landed-kg ..}
     :water-body is the named river or lake the vessel is permitted to fish
     (there is no maritime-zone/EEZ split for inland waters). :quota-kg is
     the permit's total allowed catch weight for the current period;
     :landed-kg is the cumulative weight already logged.")

  (log-catch! [st vessel-id record]
    "Commits a catch record (species, quantity-kg, location, timestamp) and
     accrues the vessel's cumulative :landed-kg. Administrative logging
     only -- never a catch decision. Returns updated store.")

  (schedule-maintenance! [st vessel-id maintenance-proposal]
    "Schedules vessel maintenance. Returns updated store.")

  (flag-safety-concern! [st vessel-id concern]
    "Logs a vessel-safety/water-level/weather concern (always escalates to
     the vessel operator/crew). Returns updated store.")

  (order-supply! [st vessel-id order]
    "Logs a supply order proposal (fuel, gear, provisions).
     Returns updated store.")

  (audit-log [st vessel-id]
    "Returns all audit entries for this vessel."))

(defn- now []
  #?(:clj (System/currentTimeMillis)
     :cljs (js/Date.now)))

(deftype MemStore [state-atom]
  Store
  (vessel [st vessel-id]
    (get-in @state-atom [:vessels vessel-id]))

  (log-catch! [st vessel-id record]
    (swap! state-atom
           (fn [s]
             (-> s
                 (update-in [:vessels vessel-id :last-catch-record]
                           (constantly (assoc record :t (now))))
                 (update-in [:vessels vessel-id :landed-kg] (fnil + 0)
                           (get record :quantity-kg 0))
                 (update-in [:audit vessel-id] (fnil conj [])
                           {:t (now)
                            :type :catch-logged
                            :record record}))))
    st)

  (schedule-maintenance! [st vessel-id maintenance-proposal]
    (swap! state-atom
           (fn [s]
             (-> s
                 (update-in [:vessels vessel-id :pending-maintenance] (fnil conj [])
                           (assoc maintenance-proposal :t (now)))
                 (update-in [:audit vessel-id] (fnil conj [])
                           {:t (now)
                            :type :maintenance-scheduled
                            :proposal maintenance-proposal}))))
    st)

  (flag-safety-concern! [st vessel-id concern]
    (swap! state-atom
           (fn [s]
             (-> s
                 (update-in [:vessels vessel-id :safety-concerns] (fnil conj [])
                           (assoc concern :t (now)))
                 (update-in [:audit vessel-id] (fnil conj [])
                           {:t (now)
                            :type :safety-concern-flagged
                            :concern concern
                            :escalated true}))))
    st)

  (order-supply! [st vessel-id order]
    (swap! state-atom
           (fn [s]
             (-> s
                 (update-in [:vessels vessel-id :supply-orders] (fnil conj [])
                           (assoc order :t (now)))
                 (update-in [:audit vessel-id] (fnil conj [])
                           {:t (now)
                            :type :supply-ordered
                            :order order}))))
    st)

  (audit-log [st vessel-id]
    (get-in @state-atom [:audit vessel-id] [])))

(defn mem-store
  "Creates an in-memory freshwater fishing fleet store, optionally
  initialized with fixtures."
  [& [{:keys [fixtures]}]]
  (let [state (cond-> {:vessels {} :audit {}}
                fixtures (update :vessels merge fixtures))]
    (MemStore. (atom state))))
