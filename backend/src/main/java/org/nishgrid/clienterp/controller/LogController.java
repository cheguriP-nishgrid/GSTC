package org.nishgrid.clienterp.controller;

import org.nishgrid.clienterp.model.PurchaseAuditLog;
import org.nishgrid.clienterp.model.PurchaseStatusLog;
import org.nishgrid.clienterp.repository.PurchaseAuditLogRepository;
import org.nishgrid.clienterp.repository.PurchaseStatusLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    @Autowired
    private PurchaseAuditLogRepository auditLogRepository;

    @Autowired
    private PurchaseStatusLogRepository statusLogRepository;

    @GetMapping("/audit")
    public List<PurchaseAuditLog> getAuditLogs() {
        return auditLogRepository.findAll();
    }

    @GetMapping("/status")
    public List<PurchaseStatusLog> getStatusLogs() {
        return statusLogRepository.findAll();
    }
}