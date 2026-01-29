package org.nishgrid.clienterp.listener;

import org.nishgrid.clienterp.event.SaleAuditEvent;
import org.nishgrid.clienterp.service.SmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SmsNotificationListener {

    @Autowired
    private SmsService smsService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSaleCreated(SaleAuditEvent event) {
        if ("CREATED".equals(event.getAction())) {
            smsService.sendInvoiceSms(event.getNewState());
        }
    }
}