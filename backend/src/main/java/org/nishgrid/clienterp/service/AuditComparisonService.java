package org.nishgrid.clienterp.service;

import org.nishgrid.clienterp.dto.DebitNoteResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AuditComparisonService {

    public static class ChangeSummary {
        private final List<String> oldValues = new ArrayList<>();
        private final List<String> newValues = new ArrayList<>();

        public void addChange(String fieldName, Object oldValue, Object newValue) {
            oldValues.add(String.format("%s: '%s'", fieldName, oldValue));
            newValues.add(String.format("%s: '%s'", fieldName, newValue));
        }

        public String getOldValuesAsString() {
            return String.join("; ", oldValues);
        }

        public String getNewValuesAsString() {
            return String.join("; ", newValues);
        }

        public boolean isEmpty() {
            return oldValues.isEmpty();
        }
    }

    public ChangeSummary compare(DebitNoteResponse oldState, DebitNoteResponse newState) {
        ChangeSummary summary = new ChangeSummary();

        if (!Objects.equals(oldState.getDebitNoteNo(), newState.getDebitNoteNo())) {
            summary.addChange("Debit Note #", oldState.getDebitNoteNo(), newState.getDebitNoteNo());
        }
        if (!Objects.equals(oldState.getDebitNoteDate(), newState.getDebitNoteDate())) {
            summary.addChange("Date", oldState.getDebitNoteDate(), newState.getDebitNoteDate());
        }
        if (!Objects.equals(oldState.getVendorId(), newState.getVendorId())) {
            summary.addChange("Vendor", oldState.getVendorName(), newState.getVendorName());
        }
        if (!Objects.equals(oldState.getPurchaseInvoiceId(), newState.getPurchaseInvoiceId())) {
            summary.addChange("Invoice", oldState.getPurchaseInvoiceNumber(), newState.getPurchaseInvoiceNumber());
        }
        if (!Objects.equals(oldState.getReason(), newState.getReason())) {
            summary.addChange("Reason", oldState.getReason(), newState.getReason());
        }
        if (!Objects.equals(oldState.getStatus(), newState.getStatus())) {
            summary.addChange("Status", oldState.getStatus(), newState.getStatus());
        }
        if (!Objects.equals(oldState.getTotalAmountWithGst(), newState.getTotalAmountWithGst())) {
            summary.addChange("Total Amount", oldState.getTotalAmountWithGst(), newState.getTotalAmountWithGst());
        }
        if (!Objects.equals(oldState.getApprovedBy(), newState.getApprovedBy())) {
            summary.addChange("Approved By", oldState.getApprovedBy(), newState.getApprovedBy());
        }


        return summary;
    }
}