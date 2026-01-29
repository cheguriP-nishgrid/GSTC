
package org.nishgrid.clienterp.service;

import jakarta.persistence.EntityNotFoundException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.nishgrid.clienterp.dto.*;
import org.nishgrid.clienterp.event.SaleAuditEvent;
import org.nishgrid.clienterp.exception.ResourceNotFoundException;
import org.nishgrid.clienterp.model.*;
import org.nishgrid.clienterp.repository.CustomerRepository;
import org.nishgrid.clienterp.repository.Gstr1ItemRepository;
import org.nishgrid.clienterp.repository.SalesInvoiceRepository;
import org.nishgrid.clienterp.repository.SalesReturnRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SaleService {

    @Autowired
    private SalesInvoiceRepository salesInvoiceRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private Gstr1ItemRepository gstr1ItemRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private InvoicePrintService invoicePrintService;
    @Autowired
    private SalesReturnRepository salesReturnRepository;
    @Autowired
    private EmailService emailService;

    private static final int STANDARD_SCALE = 2;
    private static final RoundingMode STANDARD_ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");

    @Transactional
    public SalesInvoice saveFullSale(SaleRequestDTO request) {
        Customer customer = customerRepository.findByMobile(request.getCustomerMobile())
                .orElseGet(() -> createNewCustomer(request));
        SalesInvoice invoice = new SalesInvoice();
        invoice.setCustomer(customer);
        invoice.setInvoiceNo(generateNextInvoiceNumber());
        invoice.setInvoiceDate(Optional.ofNullable(request.getInvoiceDate()).orElse(LocalDate.now()));
        invoice.setCreatedAt(LocalDate.now());
        invoice.setRemarks(request.getRemarks());
        invoice.setStatus("PENDING");
        invoice.setPaymentMode("Other".equalsIgnoreCase(request.getPaymentMode()) && request.getOtherPaymentMode() != null
                ? request.getOtherPaymentMode() : request.getPaymentMode());
        invoice.setOldGoldValue(safe(request.getOldGoldValue()));
        request.getItems().forEach(dto -> {
            SalesItem item = new SalesItem();
            item.setItemName(dto.getItemName());
            item.setHsnCode(dto.getHsnCode());
            item.setPurity(dto.getPurity());
            item.setGrossWeight(dto.getGrossWeight());
            item.setNetWeight(dto.getNetWeight());
            item.setRatePerGram(dto.getRatePerGram());
            item.setDiamondCarat(dto.getDiamondCarat());
            item.setDiamondRate(dto.getDiamondRate());
            item.setMakingCharge(dto.getMakingCharge());
            BigDecimal netWeight = BigDecimal.valueOf(Optional.ofNullable(dto.getNetWeight()).orElse(0.0));
            BigDecimal rate = Optional.ofNullable(dto.getRatePerGram()).orElse(BigDecimal.ZERO);
            BigDecimal goldValue = netWeight.multiply(rate);
            BigDecimal diamondCarat = Optional.ofNullable(dto.getDiamondCarat()).orElse(BigDecimal.ZERO);
            BigDecimal diamondRate = Optional.ofNullable(dto.getDiamondRate()).orElse(BigDecimal.ZERO);
            BigDecimal diamondValue = diamondCarat.multiply(diamondRate);
            item.setDiamondAmount(diamondValue.setScale(STANDARD_SCALE, STANDARD_ROUNDING));
            BigDecimal basePrice = goldValue.add(diamondValue);
            BigDecimal fixedMakingCharge = Optional.ofNullable(dto.getMakingChargeAmount()).orElse(BigDecimal.ZERO);
            BigDecimal makingChargeValue = fixedMakingCharge.compareTo(BigDecimal.ZERO) > 0
                    ? fixedMakingCharge
                    : basePrice.multiply(Optional.ofNullable(dto.getMakingCharge()).orElse(BigDecimal.ZERO))
                    .divide(HUNDRED, STANDARD_SCALE, STANDARD_ROUNDING);
            item.setMakingChargeAmount(makingChargeValue);
            BigDecimal itemTotalPrice = basePrice.add(makingChargeValue);
            item.setTotalPrice(itemTotalPrice.setScale(STANDARD_SCALE, STANDARD_ROUNDING));
            invoice.addSalesItem(item);
        });
        calculateAndSetInvoiceTotals(invoice, request.getDiscountAmount(), request.getDiscountPercent(), request.getGstPercent());
        if (invoice.getPaidAmount() == null) invoice.setPaidAmount(BigDecimal.ZERO);
        recalcDueAmount(invoice);
        SalesInvoice savedInvoice = salesInvoiceRepository.save(invoice);
        eventPublisher.publishEvent(new SaleAuditEvent("CREATED", null, savedInvoice));
        BigDecimal totalTaxableValueForInvoice = savedInvoice.getSalesItems().stream()
                .map(SalesItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        for (SalesItem item : savedInvoice.getSalesItems()) {
            Gstr1Item gstr1 = new Gstr1Item();
            gstr1.setSalesInvoice(savedInvoice);
            gstr1.setCustomerGstin(savedInvoice.getCustomer().getGstin());
            gstr1.setInvoiceDate(savedInvoice.getInvoiceDate());
            gstr1.setItemHsn(item.getHsnCode());
            BigDecimal itemTaxableValue = item.getTotalPrice();
            gstr1.setTaxableValue(itemTaxableValue.doubleValue());
            gstr1.setGstRate(savedInvoice.getGstPercent().doubleValue());
            if (totalTaxableValueForInvoice.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal ratio = itemTaxableValue.divide(totalTaxableValueForInvoice, 4, RoundingMode.HALF_UP);
                gstr1.setCgstAmount(savedInvoice.getCgst().multiply(ratio).doubleValue());
                gstr1.setSgstAmount(savedInvoice.getSgst().multiply(ratio).doubleValue());
                gstr1.setIgstAmount(savedInvoice.getIgst().multiply(ratio).doubleValue());
            } else {
                gstr1.setCgstAmount(0.0);
                gstr1.setSgstAmount(0.0);
                gstr1.setIgstAmount(0.0);
            }
            gstr1.setExportMonth(String.format("%d-%02d", savedInvoice.getInvoiceDate().getYear(), savedInvoice.getInvoiceDate().getMonthValue()));
            gstr1ItemRepository.save(gstr1);
        }
        return savedInvoice;
    }

    @Transactional
    public SalesInvoice updateSale(Long id, SaleUpdateDTO updateDto) {
        SalesInvoice invoice = salesInvoiceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found with id: " + id));
        String oldStatus = invoice.getStatus();
        Map<String, Object> oldState = new HashMap<>();
        oldState.put("status", oldStatus);
        oldState.put("remarks", invoice.getRemarks());
        if (updateDto.getInvoiceDate() != null) invoice.setInvoiceDate(updateDto.getInvoiceDate());
        if (updateDto.getPaymentMode() != null && !updateDto.getPaymentMode().isBlank()) invoice.setPaymentMode(updateDto.getPaymentMode());
        if (updateDto.getRemarks() != null) invoice.setRemarks(updateDto.getRemarks());
        if (updateDto.getOldGoldValue() != null) invoice.setOldGoldValue(updateDto.getOldGoldValue());
        if (updateDto.getStatus() != null && !updateDto.getStatus().isBlank()) {
            if (("PAID".equalsIgnoreCase(oldStatus) || "PARTIALLY PAID".equalsIgnoreCase(oldStatus)) &&
                    !("CANCELLED".equalsIgnoreCase(updateDto.getStatus())))
                throw new IllegalStateException("Cannot manually change status of a paid invoice, except to cancel.");
            if ("CANCELLED".equalsIgnoreCase(updateDto.getStatus())) {
                invoice.setStatus("CANCELLED");
                invoice.setPaidAmount(BigDecimal.ZERO);
            } else invoice.setStatus(updateDto.getStatus());
        }
        if (updateDto.getItems() != null && !updateDto.getItems().isEmpty()) {
            gstr1ItemRepository.deleteAll(gstr1ItemRepository.findBySalesInvoiceInvoiceId(id));
            invoice.getSalesItems().clear();
            updateDto.getItems().forEach(dto -> {
                SalesItem item = new SalesItem();
                item.setItemName(dto.getItemName());
                item.setHsnCode(dto.getHsnCode());
                item.setPurity(dto.getPurity());
                item.setGrossWeight(dto.getGrossWeight());
                item.setNetWeight(dto.getNetWeight());
                item.setRatePerGram(dto.getRatePerGram());
                item.setDiamondCarat(dto.getDiamondCarat());
                item.setDiamondRate(dto.getDiamondRate());
                item.setMakingCharge(dto.getMakingCharge());
                BigDecimal netWeight = BigDecimal.valueOf(Optional.ofNullable(dto.getNetWeight()).orElse(0.0));
                BigDecimal rate = Optional.ofNullable(dto.getRatePerGram()).orElse(BigDecimal.ZERO);
                BigDecimal goldValue = netWeight.multiply(rate);
                BigDecimal diamondCarat = Optional.ofNullable(dto.getDiamondCarat()).orElse(BigDecimal.ZERO);
                BigDecimal diamondRate = Optional.ofNullable(dto.getDiamondRate()).orElse(BigDecimal.ZERO);
                BigDecimal diamondValue = diamondCarat.multiply(diamondRate);
                item.setDiamondAmount(diamondValue.setScale(STANDARD_SCALE, STANDARD_ROUNDING));
                BigDecimal basePrice = goldValue.add(diamondValue);
                BigDecimal fixedMakingCharge = Optional.ofNullable(dto.getMakingChargeAmount()).orElse(BigDecimal.ZERO);
                BigDecimal makingChargeValue = fixedMakingCharge.compareTo(BigDecimal.ZERO) > 0
                        ? fixedMakingCharge
                        : basePrice.multiply(Optional.ofNullable(dto.getMakingCharge()).orElse(BigDecimal.ZERO))
                        .divide(HUNDRED, STANDARD_SCALE, STANDARD_ROUNDING);
                item.setMakingChargeAmount(makingChargeValue);
                BigDecimal itemTotalPrice = basePrice.add(makingChargeValue);
                item.setTotalPrice(itemTotalPrice.setScale(STANDARD_SCALE, STANDARD_ROUNDING));
                invoice.addSalesItem(item);
            });
            calculateAndSetInvoiceTotals(invoice, updateDto.getDiscountAmount(), updateDto.getDiscountPercent(), updateDto.getGstPercent());
        }
        recalcDueAmount(invoice);
        if (!"CANCELLED".equalsIgnoreCase(invoice.getStatus())) {
            if (invoice.getDueAmount().compareTo(BigDecimal.ZERO) == 0) invoice.setStatus("PAID");
            else if (invoice.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) invoice.setStatus("PARTIALLY PAID");
            else if (invoice.getStatus() == null || invoice.getStatus().isBlank()) invoice.setStatus("PENDING");
        }
        SalesInvoice updatedInvoice = salesInvoiceRepository.save(invoice);
        eventPublisher.publishEvent(new SaleAuditEvent("UPDATED", oldState, updatedInvoice));
        if (updateDto.getItems() != null && !updateDto.getItems().isEmpty()) {
            BigDecimal totalTaxableValueForInvoice = updatedInvoice.getSalesItems().stream()
                    .map(SalesItem::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            for (SalesItem item : updatedInvoice.getSalesItems()) {
                Gstr1Item gstr1 = new Gstr1Item();
                gstr1.setSalesInvoice(updatedInvoice);
                gstr1.setCustomerGstin(updatedInvoice.getCustomer().getGstin());
                gstr1.setInvoiceDate(updatedInvoice.getInvoiceDate());
                gstr1.setItemHsn(item.getHsnCode());
                BigDecimal itemTaxableValue = item.getTotalPrice();
                gstr1.setTaxableValue(itemTaxableValue.doubleValue());
                gstr1.setGstRate(updatedInvoice.getGstPercent().doubleValue());
                if (totalTaxableValueForInvoice.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal ratio = itemTaxableValue.divide(totalTaxableValueForInvoice, 4, RoundingMode.HALF_UP);
                    gstr1.setCgstAmount(updatedInvoice.getCgst().multiply(ratio).doubleValue());
                    gstr1.setSgstAmount(updatedInvoice.getSgst().multiply(ratio).doubleValue());
                    gstr1.setIgstAmount(updatedInvoice.getIgst().multiply(ratio).doubleValue());
                } else {
                    gstr1.setCgstAmount(0.0);
                    gstr1.setSgstAmount(0.0);
                    gstr1.setIgstAmount(0.0);
                }
                gstr1.setExportMonth(String.format("%d-%02d", updatedInvoice.getInvoiceDate().getYear(), updatedInvoice.getInvoiceDate().getMonthValue()));
                gstr1ItemRepository.save(gstr1);
            }
        }
        return updatedInvoice;
    }

    /**
     * The old simple return method is removed. The correct way to process returns is through the
     * ReturnService.processReturns method, which handles item-level tracking and updates the
     * invoice status/total amount correctly via SalesReturn records.
     */

    @Transactional(readOnly = true)
    public SalesListResponse getSalesAuditData(LocalDate startDate, LocalDate endDate, List<String> statuses) {
        List<SalesInvoice> invoices = salesInvoiceRepository.findByInvoiceDateBetweenAndStatusIn(startDate, endDate, statuses);
        List<SalesRecordDto> salesRecords = invoices.stream()
                .map(invoice -> {
                    BigDecimal totalReturnedAmount = salesReturnRepository.findTotalReturnedAmountByInvoiceId(invoice.getInvoiceId())
                            .orElse(BigDecimal.ZERO);
                    // Base Net Amount is what the customer should have paid before old gold and returns
                    BigDecimal baseNetAmount = safe(invoice.getNetAmount());

                    // FIXED: Final amount is Net Amount PLUS Old Gold value MINUS total returned amount
                    // Final Amount = Net Amount + Old Gold Value - Total Returned Amount
                    BigDecimal finalAmount = baseNetAmount
                            .add(safe(invoice.getOldGoldValue()))
                            .subtract(totalReturnedAmount);

                    SalesRecordDto dto = new SalesRecordDto();
                    dto.setId(invoice.getInvoiceId());
                    dto.setInvoiceNo(invoice.getInvoiceNo());
                    dto.setInvoiceDate(invoice.getInvoiceDate());
                    dto.setCustomerName(invoice.getCustomer().getName());
                    dto.setNetAmount(baseNetAmount); // This is the amount *before* deducting returns/old gold
                    dto.setFinalAmount(finalAmount); // This is the actual value based on the new logic
                    dto.setOldGoldValue(safe(invoice.getOldGoldValue()));
                    // Need to recalculate due amount here to ensure it's up-to-date
                    recalcDueAmount(invoice);
                    dto.setDueAmount(invoice.getDueAmount());
                    return dto;
                })
                .collect(Collectors.toList());
        BigDecimal totalFinalAmount = salesRecords.stream()
                .map(SalesRecordDto::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SalesListResponse(salesRecords, totalFinalAmount);
    }

    private SalesRecordDto convertToSaleRecordDto(SalesInvoice invoice) {
        recalcDueAmount(invoice);
        SalesRecordDto dto = new SalesRecordDto();
        dto.setId(invoice.getInvoiceId());
        dto.setInvoiceNo(invoice.getInvoiceNo());
        if (invoice.getCustomer() != null) {
            dto.setCustomerName(invoice.getCustomer().getName());
        }
        dto.setInvoiceDate(invoice.getInvoiceDate());
        dto.setSalesType(invoice.getSalesType());
        dto.setPaymentMode(invoice.getPaymentMode());
        dto.setTotalAmount(invoice.getTotalAmount());
        dto.setNetAmount(invoice.getNetAmount());
        dto.setStatus(invoice.getStatus());
        dto.setPaidAmount(invoice.getPaidAmount());
        dto.setDueAmount(invoice.getDueAmount());
        dto.setOldGoldValue(invoice.getOldGoldValue());
        return dto;
    }

    /**
     * Recalculates the due amount.
     * Due = Final Payable Amount - Paid Amount
     * The final payable amount is Net Amount - Old Gold Value - Total Returned Amount
     * If the Final Amount is now calculated as (Net Amount + Old Gold Value - Total Returned Amount),
     * we must update the Due calculation to be consistent.
     * Let's assume Due Amount should still reflect the amount remaining for the customer to pay for the goods kept.
     * Amount Customer Must Pay = Net Amount (goods value) - Old Gold Value (credit) - Total Returned Amount (refunded value)
     * Due Amount = Amount Customer Must Pay - Paid Amount
     * @param invoice The SalesInvoice to update.
     */
    private void recalcDueAmount(SalesInvoice invoice) {
        // Fetch the total returned amount for the invoice (the amount actually refunded/credited)
        BigDecimal totalReturnedAmount = salesReturnRepository.findTotalReturnedAmountByInvoiceId(invoice.getInvoiceId())
                .orElse(BigDecimal.ZERO);

        // Amount Customer Must Pay (Value of goods kept - Old Gold Credit)
        BigDecimal amountCustomerMustPay = safe(invoice.getNetAmount())
                .subtract(safe(invoice.getOldGoldValue()))
                .subtract(totalReturnedAmount);

        // Due = Amount Customer Must Pay - Paid Amount
        BigDecimal due = amountCustomerMustPay
                .subtract(safe(invoice.getPaidAmount()));

        if (due.compareTo(BigDecimal.ZERO) < 0) {
            // Cap at zero for "Due"
            due = BigDecimal.ZERO;
        }
        invoice.setDueAmount(due.setScale(STANDARD_SCALE, STANDARD_ROUNDING));
    }

    public byte[] exportSalesToExcel(LocalDate startDate, LocalDate endDate, List<String> statuses) throws IOException {
        List<SalesInvoice> invoices = salesInvoiceRepository.findByInvoiceDateBetweenAndStatusIn(startDate, endDate, statuses);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sales Data");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            String[] headers = {"Invoice No", "Date", "Customer Name", "Total Amount", "Discount", "CGST", "SGST", "IGST", "GST Amount", "Round Off", "Net Amount", "Paid Amount", "Due Amount", "Old Gold Value", "Total Returned"}; // Added Total Returned column
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowNum = 1;
            for (SalesInvoice invoice : invoices) {
                recalcDueAmount(invoice);
                BigDecimal totalReturned = salesReturnRepository.findTotalReturnedAmountByInvoiceId(invoice.getInvoiceId()).orElse(BigDecimal.ZERO);

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(Optional.ofNullable(invoice.getInvoiceNo()).orElse(""));
                row.createCell(1).setCellValue(invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toString() : "");
                row.createCell(2).setCellValue(invoice.getCustomer() != null ? Optional.ofNullable(invoice.getCustomer().getName()).orElse("") : "");
                row.createCell(3).setCellValue(safeDouble(invoice.getTotalAmount()));
                row.createCell(4).setCellValue(safeDouble(invoice.getDiscount()));
                row.createCell(5).setCellValue(safeDouble(invoice.getCgst()));
                row.createCell(6).setCellValue(safeDouble(invoice.getSgst()));
                row.createCell(7).setCellValue(safeDouble(invoice.getIgst()));
                row.createCell(8).setCellValue(safeDouble(invoice.getGstAmount()));
                row.createCell(9).setCellValue(safeDouble(invoice.getRoundOff()));
                row.createCell(10).setCellValue(safeDouble(invoice.getNetAmount()));
                row.createCell(11).setCellValue(safeDouble(invoice.getPaidAmount()));
                row.createCell(12).setCellValue(safeDouble(invoice.getDueAmount()));
                row.createCell(13).setCellValue(safeDouble(invoice.getOldGoldValue()));
                row.createCell(14).setCellValue(safeDouble(totalReturned)); // Added total returned amount
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                workbook.write(baos);
                return baos.toByteArray();
            }
        }
    }

    public byte[] exportInvoiceToPdf(Long invoiceId) throws IOException {
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));
        recalcDueAmount(invoice); // Ensure due amount is correct before printing
        return invoicePrintService.generatePdfForInvoice(invoice);
    }

    public String getNextInvoiceNumber() {
        return generateNextInvoiceNumber();
    }

    private void calculateAndSetInvoiceTotals(SalesInvoice invoice, BigDecimal discountAmount, BigDecimal discountPercent, BigDecimal gstPercent) {
        gstPercent = Optional.ofNullable(gstPercent).orElse(BigDecimal.ZERO);
        invoice.setGstPercent(gstPercent);
        BigDecimal totalAmount = invoice.getSalesItems().stream()
                .map(SalesItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.setTotalAmount(totalAmount.setScale(STANDARD_SCALE, STANDARD_ROUNDING));
        BigDecimal subtotal = totalAmount;
        BigDecimal gstAmount = subtotal.multiply(gstPercent).divide(HUNDRED, STANDARD_SCALE, STANDARD_ROUNDING);
        invoice.setGstAmount(gstAmount.setScale(STANDARD_SCALE, STANDARD_ROUNDING));
        BigDecimal cgst = gstAmount.divide(TWO, STANDARD_SCALE, STANDARD_ROUNDING);
        invoice.setCgst(cgst.setScale(STANDARD_SCALE, STANDARD_ROUNDING));
        invoice.setSgst(gstAmount.subtract(cgst).setScale(STANDARD_SCALE, STANDARD_ROUNDING));
        invoice.setIgst(BigDecimal.ZERO);
        BigDecimal amountBeforeDiscount = subtotal.add(gstAmount);
        BigDecimal finalDiscountAmount = Optional.ofNullable(discountAmount).orElse(BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0
                ? discountAmount
                : amountBeforeDiscount.multiply(Optional.ofNullable(discountPercent).orElse(BigDecimal.ZERO))
                .divide(HUNDRED, STANDARD_SCALE, STANDARD_ROUNDING);
        invoice.setDiscount(finalDiscountAmount.setScale(STANDARD_SCALE, STANDARD_ROUNDING));
        BigDecimal grandTotal = amountBeforeDiscount.subtract(finalDiscountAmount);
        BigDecimal roundedAmount = grandTotal.setScale(0, RoundingMode.HALF_UP);
        invoice.setNetAmount(roundedAmount.setScale(STANDARD_SCALE, STANDARD_ROUNDING));
        invoice.setRoundOff(roundedAmount.subtract(grandTotal).setScale(STANDARD_SCALE, STANDARD_ROUNDING));
    }
    private Customer createNewCustomer(SaleRequestDTO request) {
        Customer newCustomer = new Customer();
        newCustomer.setName(request.getCustomerName());
        newCustomer.setMobile(request.getCustomerMobile());
        newCustomer.setEmail(request.getCustomerEmail());
        newCustomer.setAddress(request.getCustomerAddress());
        newCustomer.setGstin(request.getCustomerGstin());
        newCustomer.setCreatedAt(LocalDate.now());
        return customerRepository.save(newCustomer);
    }

    private synchronized String generateNextInvoiceNumber() {
        Pageable pageRequest = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "invoiceId"));
        List<String> latestInvoices = salesInvoiceRepository.findInvoiceNumbers(pageRequest);
        String latestInvoiceNo = latestInvoices.isEmpty() ? null : latestInvoices.get(0);
        int currentYear = Year.now().getValue();
        int nextId = 1;
        if (latestInvoiceNo != null && latestInvoiceNo.startsWith("INV-" + currentYear)) {
            try {
                String[] parts = latestInvoiceNo.split("-");
                if (parts.length > 2) nextId = Integer.parseInt(parts[2]) + 1;
            } catch (Exception ignored) {
                nextId = 1;
            }
        }
        return String.format("INV-%d-%06d", currentYear, nextId);
    }

    private BigDecimal safe(BigDecimal value) {
        return Optional.ofNullable(value).orElse(BigDecimal.ZERO);
    }

    private double safeDouble(BigDecimal value) {
        return safe(value).doubleValue();
    }

    public List<InvoiceRowDTO> getAllInvoicesAsDTOs() {
        List<SalesInvoice> invoices = salesInvoiceRepository.findAll(Sort.by(Sort.Direction.DESC, "invoiceId"));
        return invoices.stream()
                .peek(this::recalcDueAmount)
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<InvoiceRowDTO> searchInvoicesAsDTOs(String searchTerm, String status) {
        Specification<SalesInvoice> spec = SalesInvoiceSpecification.findByCriteria(searchTerm, status);
        List<SalesInvoice> invoices = salesInvoiceRepository.findAll(spec);
        return invoices.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private InvoiceRowDTO convertToDto(SalesInvoice invoice) {
        recalcDueAmount(invoice);
        InvoiceRowDTO dto = new InvoiceRowDTO();
        dto.setId(invoice.getInvoiceId());
        dto.setInvoiceNo(invoice.getInvoiceNo());
        if (invoice.getCustomer() != null) {
            dto.setCustomerName(invoice.getCustomer().getName());
        }
        dto.setInvoiceDate(invoice.getInvoiceDate());
        dto.setSalesType(invoice.getSalesType());
        dto.setPaymentMode(invoice.getPaymentMode());
        dto.setTotalAmount(invoice.getTotalAmount());
        dto.setNetAmount(invoice.getNetAmount());
        dto.setStatus(invoice.getStatus());
        dto.setPaidAmount(invoice.getPaidAmount());
        dto.setDueAmount(invoice.getDueAmount());
        dto.setOldGoldValue(invoice.getOldGoldValue());
        return dto;
    }

    public List<SalesInvoice> getAllInvoices() {
        List<SalesInvoice> invoices = salesInvoiceRepository.findAll(Sort.by(Sort.Direction.DESC, "invoiceId"));
        invoices.forEach(this::recalcDueAmount);
        return invoices;
    }

    public List<SalesInvoice> searchInvoices(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAllInvoices();
        }
        List<SalesInvoice> invoices = salesInvoiceRepository.searchInvoices(searchTerm.trim());
        invoices.forEach(this::recalcDueAmount);
        return invoices;
    }

    @Transactional
    public List<SalesItemSelectionDTO> getItemsForInvoice(Long invoiceId) {
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));

        return invoice.getSalesItems().stream()
                .map(item -> new SalesItemSelectionDTO(item.getSalesItemId(), item.getItemName()))
                .collect(Collectors.toList());
    }
    @Transactional
    public SalesInvoice addPayment(Long invoiceId, PaymentRequestDTO paymentDto) {
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found with id: " + invoiceId));

        recalcDueAmount(invoice); // Recalculate due amount before processing payment

        if ("PAID".equalsIgnoreCase(invoice.getStatus()) || "CANCELLED".equalsIgnoreCase(invoice.getStatus())) {
            throw new IllegalStateException("Invoice is already fully settled or cancelled.");
        }
        BigDecimal paymentAmount = safe(paymentDto.getAmount());
        if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive.");
        }

        BigDecimal remainingDue = invoice.getDueAmount();

        if (remainingDue.compareTo(BigDecimal.ZERO) > 0 && paymentAmount.compareTo(remainingDue) > 0) {
            throw new IllegalArgumentException("Payment of " + paymentAmount + " exceeds the remaining due amount of " + remainingDue);
        } else if (remainingDue.compareTo(BigDecimal.ZERO) == 0 && paymentAmount.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("Invoice is fully paid/settled (Due: 0.00). No further payments allowed.");
        }


        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setAmount(paymentAmount);
        transaction.setPaymentDate(Optional.ofNullable(paymentDto.getPaymentDate()).orElse(LocalDate.now()));
        transaction.setPaymentMode(paymentDto.getMode());
        transaction.setReferenceNo(paymentDto.getReferenceNo());
        transaction.setReceivedBy(paymentDto.getReceivedBy());
        invoice.addPaymentTransaction(transaction);

        BigDecimal alreadyPaid = safe(invoice.getPaidAmount());
        BigDecimal newPaidAmount = alreadyPaid.add(paymentAmount);
        invoice.setPaidAmount(newPaidAmount.setScale(STANDARD_SCALE, STANDARD_ROUNDING));

        recalcDueAmount(invoice);

        String oldStatus = invoice.getStatus();

        if (invoice.getDueAmount().compareTo(BigDecimal.ZERO) == 0) {
            invoice.setStatus("PAID");
        } else {
            invoice.setStatus("PARTIALLY PAID");
        }

        SalesInvoice savedInvoice = salesInvoiceRepository.save(invoice);

        if ("PAID".equalsIgnoreCase(savedInvoice.getStatus()) && !"PAID".equalsIgnoreCase(oldStatus)) {
            byte[] pdfBytes = invoicePrintService.generatePdfForInvoice(savedInvoice);
            String subject = "Payment Confirmed for Invoice #" + savedInvoice.getInvoiceNo();
            String body = "Dear " + savedInvoice.getCustomer().getName() + ",<br><br>"
                    + "This is to confirm that your payment for invoice #" + savedInvoice.getInvoiceNo()
                    + " has been successfully received and your invoice is now fully paid."
                    + "Thank you for your business. Please find the attached invoice for your records."
                    + "<br><br>Sincerely,<br>The Nishgrid Team";
            emailService.sendInvoiceEmail(savedInvoice, subject, body, pdfBytes, "Invoice_" + savedInvoice.getInvoiceNo() + ".pdf");
        }

        return savedInvoice;
    }
}