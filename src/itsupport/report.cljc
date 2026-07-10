(ns itsupport.report
  "Disclosure rendering — output as a GOVERNED read. The column set is
  whatever the TicketGovernor's licensed-disclosure gate approved for the
  caller's contract tier."
  (:require [itsupport.store :as store]))

(defn render-ticket
  [db ticket-id columns]
  (let [tk  (store/ticket db ticket-id)
        asn (store/assignment db ticket-id)
        cell (fn [col]
               (case col
                 :id                    ticket-id
                 :client                (:client tk)
                 :category              (:category tk)
                 :required-access-tier  (:required-access-tier tk)
                 :sla-remaining-minutes (:sla-remaining-minutes tk)
                 :assigned-technician   (:technician-id asn)
                 :raw-source            (:source asn)
                 nil))]
    (into {} (map (juxt identity cell)) columns)))
