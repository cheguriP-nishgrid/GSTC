package org.nishgrid.clienterp.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.nishgrid.clienterp.event.SaleAuditEvent;
import org.nishgrid.clienterp.model.SalesAuditLog;
import org.nishgrid.clienterp.model.SalesInvoice;
import org.nishgrid.clienterp.repository.SalesAuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class AuditLogEventListener {

    @Autowired
    private SalesAuditLogRepository auditLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSaleAuditEvent(SaleAuditEvent event) {
        SalesInvoice invoice = event.getNewState();

        try {
            SalesAuditLog log = new SalesAuditLog();
            log.setAction(event.getAction());
            log.setPerformedBy(invoice.getCreatedBy());
            log.setTimestamp(LocalDateTime.now());
            log.setSalesInvoice(invoice);

            Map<String, Object> newStateMap = new HashMap<>();
            newStateMap.put("invoiceId", invoice.getInvoiceId());
            newStateMap.put("invoiceNo", invoice.getInvoiceNo());
            newStateMap.put("netAmount", invoice.getNetAmount());
            newStateMap.put("status", invoice.getStatus());
            newStateMap.put("remarks", invoice.getRemarks());

            log.setNewData(objectMapper.writeValueAsString(newStateMap));

            if ("CREATED".equals(event.getAction())) {
                log.setOldData("{}");
            } else {
                Map<String, Object> oldState = event.getOldState();
                log.setOldData(oldState != null ? objectMapper.writeValueAsString(oldState) : "{}");
            }

            auditLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("Error creating audit log: " + e.getMessage());
        }
    }
}