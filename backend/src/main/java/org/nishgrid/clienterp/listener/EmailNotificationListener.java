package org.nishgrid.clienterp.listener;

import org.nishgrid.clienterp.event.SaleAuditEvent;
import org.nishgrid.clienterp.model.Customer;
import org.nishgrid.clienterp.model.SalesInvoice;
import org.nishgrid.clienterp.repository.SalesInvoiceRepository;
import org.nishgrid.clienterp.service.EmailService;
import org.nishgrid.clienterp.service.InvoicePrintService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EmailNotificationListener {

    @Autowired private EmailService emailService;
    @Autowired private SalesInvoiceRepository salesInvoiceRepository;
    @Autowired private InvoicePrintService invoicePrintService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSaleUpdate(SaleAuditEvent event) {
        if (!"UPDATED".equals(event.getAction()) || event.getOldState() == null) {
            return;
        }

        SalesInvoice newState = event.getNewState();
        String oldStatus = (String) event.getOldState().get("status");
        String newStatus = newState.getStatus();

        boolean wasNotPaid = !"PAID".equalsIgnoreCase(oldStatus);
        boolean isNowPaid = "PAID".equalsIgnoreCase(newStatus);

        if (wasNotPaid && isNowPaid) {

            salesInvoiceRepository.findByIdWithDetails(newState.getInvoiceId()).ifPresent(invoice -> {
                Customer customer = invoice.getCustomer();
                if (customer != null && customer.getEmail() != null && !customer.getEmail().isBlank()) {

                    byte[] pdfBytes = invoicePrintService.generateInvoicePdf(invoice.getInvoiceId());

                    String subject = "Your Invoice from RameshwarPrasad gupta & Son's: " + invoice.getInvoiceNo();
                    String body = "Dear " + customer.getName() + ",\n\nThank you for your payment. Your invoice is now fully paid. Please find a copy attached for your records.\n\nBest Regards,\nRameshwarprasad guptha and son's";
                    String attachmentName = "Invoice-" + invoice.getInvoiceNo() + ".pdf";


                    emailService.sendInvoiceEmail(invoice, subject, body, pdfBytes, attachmentName);
                }
            });
        }
    }
}