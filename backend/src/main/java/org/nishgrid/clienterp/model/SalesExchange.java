package org.nishgrid.clienterp.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_exchanges")
public class SalesExchange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long exchangeId;

    // The item that was returned
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_item_id", nullable = false, unique = true)
    private SalesItem returnedItem;

    // The NEW item purchased (linking to a SalesItem is more robust than Long,
    // but sticking to the request's need to link to a master product/item)
    @Column(name = "new_item_id", nullable = false)
    private Long newItemId;

    // --- Financial and Audit Fields ---

    // The final net difference (positive: customer pays; negative: store credits/refunds)
    @Column(name = "customer_final_adjustment", nullable = false)
    private BigDecimal customerFinalAdjustment;

    // The calculated credit value given for the returned item (Crucial Audit Field)
    @Column(name = "calculated_intrinsic_credit", nullable = false)
    private BigDecimal calculatedIntrinsicCredit;

    // The 24K gold rate used for this transaction (Crucial Audit Field)
    @Column(name = "gold_rate_used")
    private BigDecimal goldRateUsed;

    // Placeholder for other rates (Platinum, Silver) if fully implemented later
    @Column(name = "platinum_rate_used")
    private BigDecimal platinumRateUsed;

    // --- Standard Fields ---
    private LocalDateTime exchangeDate;

    @Column(columnDefinition = "TEXT")
    private String remarks;
    private String handledBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_invoice_id", nullable = false)
    private SalesInvoice originalInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    // Getters and Setters
    public Long getExchangeId() { return exchangeId; }
    public void setExchangeId(Long exchangeId) { this.exchangeId = exchangeId; }

    public SalesItem getReturnedItem() { return returnedItem; }
    public void setReturnedItem(SalesItem returnedItem) { this.returnedItem = returnedItem; }

    public Long getNewItemId() { return newItemId; }
    public void setNewItemId(Long newItemId) { this.newItemId = newItemId; }

    public LocalDateTime getExchangeDate() { return exchangeDate; }
    public void setExchangeDate(LocalDateTime exchangeDate) { this.exchangeDate = exchangeDate; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getHandledBy() { return handledBy; }
    public void setHandledBy(String handledBy) { this.handledBy = handledBy; }

    public SalesInvoice getOriginalInvoice() { return originalInvoice; }
    public void setOriginalInvoice(SalesInvoice originalInvoice) { this.originalInvoice = originalInvoice; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    // New/Updated Getters and Setters
    public BigDecimal getCustomerFinalAdjustment() { return customerFinalAdjustment; }
    public void setCustomerFinalAdjustment(BigDecimal customerFinalAdjustment) { this.customerFinalAdjustment = customerFinalAdjustment; }

    public BigDecimal getCalculatedIntrinsicCredit() { return calculatedIntrinsicCredit; }
    public void setCalculatedIntrinsicCredit(BigDecimal calculatedIntrinsicCredit) { this.calculatedIntrinsicCredit = calculatedIntrinsicCredit; }

    public BigDecimal getGoldRateUsed() { return goldRateUsed; }
    public void setGoldRateUsed(BigDecimal goldRateUsed) { this.goldRateUsed = goldRateUsed; }

    public BigDecimal getPlatinumRateUsed() { return platinumRateUsed; }
    public void setPlatinumRateUsed(BigDecimal platinumRateUsed) { this.platinumRateUsed = platinumRateUsed; }


}