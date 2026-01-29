package org.nishgrid.clienterp.service;

import org.nishgrid.clienterp.model.PurchaseAuditLog;
import org.nishgrid.clienterp.model.PurchaseOrder;
import org.nishgrid.clienterp.model.PurchaseStatusLog;
import org.nishgrid.clienterp.repository.PurchaseAuditLogRepository;
import org.nishgrid.clienterp.repository.PurchaseStatusLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    @Autowired private PurchaseAuditLogRepository auditLogRepository;
    @Autowired private PurchaseStatusLogRepository statusLogRepository;

    public void logAction(String userId, String actionType, String module, String details) {
        PurchaseAuditLog log = new PurchaseAuditLog();
        log.setUserId(userId);
        log.setActionType(actionType);
        log.setModule(module);
        log.setDetails(details);
        auditLogRepository.save(log);
    }

    public void logStatusChange(PurchaseOrder po, String oldStatus, String newStatus, String changedBy) {
        PurchaseStatusLog log = new PurchaseStatusLog();
        log.setPurchaseOrder(po);
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        log.setChangedBy(changedBy);
        statusLogRepository.save(log);
    }
}