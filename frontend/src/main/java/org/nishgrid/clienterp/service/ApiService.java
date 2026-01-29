package org.nishgrid.clienterp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.nishgrid.clienterp.dto.*;
import org.nishgrid.clienterp.model.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ApiService {

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String BASE_URL = "http://localhost:8080/api";
    private static final String LICENSE_URL = "https://client-nishgrid.co.in/lv/api";

    public static String getBaseUrl() { return BASE_URL; }
    public static String getLicenseUrl() { return LICENSE_URL; }

    private String toJson(Object object) {
        try { return objectMapper.writeValueAsString(object); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    private <T> CompletableFuture<T> sendAsync(HttpRequest request, TypeReference<T> typeReference) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    String body = response.body();
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        if (response.statusCode() == 204 || body == null || body.isBlank()) return null;
                        try { return objectMapper.readValue(body, typeReference); }
                        catch (Exception e) { throw new RuntimeException("JSON parse failed for " + request.uri(), e); }
                    }
                    throw new RuntimeException("HTTP error " + response.statusCode() + ": " + body);
                });
    }

    private HttpRequest.BodyPublisher ofMultipartData(Map<Object, Object> data, String boundary) throws IOException {
        var byteArrays = new ArrayList<byte[]>();
        String separator = "--" + boundary + "\r\nContent-Disposition: form-data; name=";

        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            byteArrays.add(separator.getBytes(StandardCharsets.UTF_8));
            if (entry.getValue() instanceof java.nio.file.Path path) {
                String mimeType = Files.probeContentType(path);
                byteArrays.add(("\"" + entry.getKey() + "\"; filename=\"" + path.getFileName()
                        + "\"\r\nContent-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                byteArrays.add(Files.readAllBytes(path));
            } else {
                byteArrays.add(("\"" + entry.getKey() + "\"\r\nContent-Type: application/json\r\n\r\n"
                        + entry.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            byteArrays.add("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        byteArrays.add(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
    }

    // ------- VENDOR API -------
    public CompletableFuture<List<Vendor>> getVendors() {
        return sendAsync(HttpRequest.newBuilder(URI.create(BASE_URL + "/vendors")).GET().build(),
                new TypeReference<>() {});
    }
    public CompletableFuture<Vendor> createVendor(Vendor vendor) {
        return sendAsync(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/vendors"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(toJson(vendor))).build(),
                new TypeReference<>() {});
    }
    public CompletableFuture<Vendor> updateVendor(long id, Vendor vendor) {
        return sendAsync(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/vendors/" + id))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(toJson(vendor))).build(),
                new TypeReference<>() {});
    }
    public CompletableFuture<Void> deleteVendor(long id) {
        return sendAsync(HttpRequest.newBuilder(URI.create(BASE_URL + "/vendors/" + id)).DELETE().build(),
                new TypeReference<>() {});
    }

    // ------- PRODUCT API -------
    public CompletableFuture<List<ProductCatalog>> getProducts() {
        return sendAsync(HttpRequest.newBuilder(URI.create(BASE_URL + "/products")).GET().build(),
                new TypeReference<>() {});
    }
    public CompletableFuture<ProductCatalog> createProduct(ProductCatalog product) {
        return sendAsync(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/products"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(toJson(product))).build(),
                new TypeReference<>() {});
    }
    public CompletableFuture<ProductCatalog> updateProduct(long id, ProductCatalog product) {
        return sendAsync(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/products/" + id))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(toJson(product))).build(),
                new TypeReference<>() {});
    }
    public CompletableFuture<Void> deleteProduct(long id) {
        return sendAsync(HttpRequest.newBuilder(URI.create(BASE_URL + "/products/" + id)).DELETE().build(),
                new TypeReference<>() {});
    }

    // --- PURCHASE ORDER METHODS ---
    public CompletableFuture<List<PurchaseOrder>> getAllPurchaseOrders() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-orders")).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<PurchaseOrder> createPurchaseOrder(PurchaseOrderRequest poRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(poRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<PurchaseOrder> updatePurchaseOrder(long id, PurchaseOrderRequest poRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-orders/" + id))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(poRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<PurchaseOrder> updatePurchaseOrderStatus(long id, PurchaseOrder.PoStatus status) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-orders/" + id + "/status"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(Map.of("status", status.toString()))))
                .build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<Void> deletePurchaseOrder(long id) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-orders/" + id)).DELETE().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    // --- GRN (GOODS RECEIPT NOTE) METHODS ---
    public CompletableFuture<List<GoodsReceiptNote>> getAllGrns() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/grn")).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<GoodsReceiptNote> createGrn(GrnRequest grnRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/grn"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(grnRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<GoodsReceiptNote> updateGrn(long id, GrnRequest grnRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/grn/" + id))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(grnRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<Void> deleteGrn(long id) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/grn/" + id)).DELETE().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    // --- PURCHASE INVOICE METHODS ---
    public CompletableFuture<List<PurchaseInvoiceResponse>> getAllInvoices() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-invoices")).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<PurchaseInvoiceResponse> createInvoice(PurchaseInvoiceRequest invoiceRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-invoices"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(invoiceRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<PurchaseInvoiceResponse> updateInvoice(long id, PurchaseInvoiceRequest invoiceRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-invoices/" + id))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(invoiceRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<Void> deleteInvoice(long id) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-invoices/" + id)).DELETE().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    // --- VENDOR PAYMENT METHODS ---

    public CompletableFuture<List<VendorPaymentResponse>> getAllPayments() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/vendor-payments")).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<VendorPaymentResponse> createPayment(VendorPaymentRequest paymentRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/vendor-payments"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(paymentRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<VendorPaymentResponse> updatePayment(long id, VendorPaymentRequest paymentRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/vendor-payments/" + id))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(paymentRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<Void> deletePayment(long id) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/vendor-payments/" + id)).DELETE().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    // --- PURCHASE RETURN METHODS ---

    public CompletableFuture<List<PurchaseReturnResponse>> getAllReturns() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-returns")).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<PurchaseReturnResponse> createReturn(PurchaseReturnRequest returnRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-returns"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(returnRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<PurchaseReturnResponse> updateReturn(long id, PurchaseReturnRequest returnRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-returns/" + id))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(returnRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<Void> deleteReturn(long id) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-returns/" + id)).DELETE().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    // --- PURCHASE DOCUMENT METHODS (Multipart) ---

    public CompletableFuture<List<PurchaseDocumentResponse>> getDocumentsForInvoice(long invoiceId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-documents/invoice/" + invoiceId)).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<PurchaseDocumentResponse> uploadDocument(long invoiceId, File file, String uploadedBy) {
        try {
            String boundary = "---" + UUID.randomUUID().toString();
            Map<Object, Object> data = Map.of(
                    "invoiceId", String.valueOf(invoiceId), // Multipart values should be strings
                    "uploadedBy", uploadedBy,
                    "file", file.toPath()
            );

            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-documents/upload"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(ofMultipartData(data, boundary)).build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            return objectMapper.readValue(response.body(), PurchaseDocumentResponse.class);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse upload response", e);
                        }
                    });
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> deleteDocument(long documentId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/purchase-documents/" + documentId)).DELETE().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    // --- LOGGING METHODS ---

    public CompletableFuture<List<PurchaseAuditLog>> getAuditLogs() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/logs/audit")).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<List<PurchaseStatusLog>> getStatusLogs() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/logs/status")).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    // --- BARCODE METHODS ---

    public CompletableFuture<List<BarcodeResponse>> createBarcodes(BarcodeRequest barcodeRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/barcodes"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(barcodeRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<List<BarcodeResponse>> getBarcodesByGrnId(long grnId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/barcodes/grn/" + grnId)).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    // --- OTHER EXPENSE METHODS (Multipart) ---

    public CompletableFuture<List<OtherExpenseResponse>> getAllExpenses() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/other-expenses")).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<OtherExpenseResponse> createExpense(OtherExpenseRequest expenseRequest, File file) {
        return handleExpenseMultipartRequest(
                URI.create(BASE_URL + "/other-expenses"),
                "POST",
                expenseRequest,
                file
        );
    }

    public CompletableFuture<OtherExpenseResponse> updateExpense(long id, OtherExpenseRequest expenseRequest, File file) {
        return handleExpenseMultipartRequest(
                URI.create(BASE_URL + "/other-expenses/" + id),
                "PUT",
                expenseRequest,
                file
        );
    }

    // Helper for creating/updating expenses with optional files
    private CompletableFuture<OtherExpenseResponse> handleExpenseMultipartRequest(URI uri, String method, OtherExpenseRequest expenseRequest, File file) {
        try {
            String boundary = "---" + UUID.randomUUID().toString();
            Map<Object, Object> data = new HashMap<>();
            data.put("expense", toJson(expenseRequest)); // Send DTO as a JSON string part
            if (file != null) {
                data.put("file", file.toPath());
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary);

            if ("POST".equalsIgnoreCase(method)) {
                requestBuilder.POST(ofMultipartData(data, boundary));
            } else {
                requestBuilder.PUT(ofMultipartData(data, boundary));
            }

            return client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            return objectMapper.readValue(response.body(), OtherExpenseResponse.class);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse expense response", e);
                        }
                    });
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> deleteExpense(long id) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/other-expenses/" + id)).DELETE().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<Void> cancelExpense(long expenseId, CancelExpenseRequest cancelRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/other-expenses/" + expenseId + "/cancel"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(cancelRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<List<CancelledExpenseLogResponse>> getCancelledExpenseLogs() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/other-expenses/cancelled-logs")).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    // --- OTHER EXPENSE RETURN & EXCHANGE METHODS (Partial: Returns only) ---
    // Note: The methods for Returns were already present in the new version, but included here for completeness.

    // ------- GENERAL DATA -------

    public CompletableFuture<List<SalesInvoiceResponse>> getAllSalesInvoices() {
        return sendAsync(HttpRequest.newBuilder(URI.create(BASE_URL + "/invoices")).GET().build(),
                new TypeReference<>() {});
    }

    public CompletableFuture<HttpResponse<String>> importSqlBackup(File file) {
        try {
            String boundary = UUID.randomUUID().toString();
            Map<Object, Object> data = Map.of("file", file.toPath());
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/backup/import/sql"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(ofMultipartData(data, boundary)).build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) { return CompletableFuture.failedFuture(e); }
    }

    public CompletableFuture<List<BackupLog>> getBackupLogs() {
        return client.sendAsync(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/backup-logs")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(response -> {
            try { return objectMapper.readValue(response.body(), new TypeReference<>() {}); }
            catch (Exception e) { return Collections.emptyList(); }
        });
    }

    // ------- BANK DETAILS -------
    public CompletableFuture<List<BankDetailsResponse>> getAllBankDetails() {
        return client.sendAsync(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/bank-details")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(response -> {
            try { return objectMapper.readValue(response.body(), new TypeReference<>() {}); }
            catch (Exception e) { return Collections.emptyList(); }
        });
    }

    public CompletableFuture<BankDetailsResponse> createBankDetails(BankDetailsRequest request, File file) {
        try {
            String boundary = UUID.randomUUID().toString();
            Map<Object, Object> data = new HashMap<>();
            data.put("details", objectMapper.writeValueAsString(request));
            if (file != null) data.put("qrCode", file.toPath());

            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(BASE_URL + "/bank-details"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(ofMultipartData(data, boundary)).build();

            return client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenApply(r -> {
                        try { return objectMapper.readValue(r.body(), BankDetailsResponse.class); }
                        catch (Exception e) { throw new RuntimeException(e); }
                    });
        } catch (Exception e) { return CompletableFuture.failedFuture(e); }
    }

    public CompletableFuture<BankDetailsResponse> updateBankDetails(long id, BankDetailsRequest request, File file) {
        try {
            String boundary = UUID.randomUUID().toString();
            Map<Object, Object> data = new HashMap<>();
            data.put("details", objectMapper.writeValueAsString(request));
            if (file != null) data.put("qrCode", file.toPath());

            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(BASE_URL + "/bank-details/" + id))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .PUT(ofMultipartData(data, boundary)).build();

            return client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenApply(r -> {
                        try { return objectMapper.readValue(r.body(), BankDetailsResponse.class); }
                        catch (Exception e) { throw new RuntimeException(e); }
                    });
        } catch (Exception e) { return CompletableFuture.failedFuture(e); }
    }

    public CompletableFuture<Void> deleteBankDetails(long id) {
        return client.sendAsync(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/bank-details/" + id)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {});
    }

    // ------- COMPANY DETAILS -------
    public CompletableFuture<List<CompanyDetails>> getAllCompanyDetails() {
        return sendAsync(HttpRequest.newBuilder(URI.create(BASE_URL + "/company-details")).GET().build(),
                new TypeReference<>() {});
    }

    public CompletableFuture<CompanyDetails> createCompanyDetails(CompanyDetails details) {
        return sendAsync(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/company-details"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(toJson(details))).build(),
                new TypeReference<>() {});
    }

    public CompletableFuture<CompanyDetails> updateCompanyDetails(long id, CompanyDetails details) {
        return sendAsync(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/company-details/" + id))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(toJson(details))).build(),
                new TypeReference<>() {});
    }

    public CompletableFuture<Void> deleteCompanyDetails(long id) {
        return sendAsync(HttpRequest.newBuilder(URI.create(BASE_URL + "/company-details/" + id)).DELETE().build(),
                new TypeReference<>() {});
    }
    public CompletableFuture<List<OtherExpenseReturnResponse>> getAllExpenseReturns() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/other-expense-returns")).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<OtherExpenseReturnResponse> createExpenseReturn(OtherExpenseReturnRequest returnRequest, File file) {
        // This logic is similar to creating an expense and could be further generalized if needed
        try {
            String boundary = "---" + UUID.randomUUID().toString();
            Map<Object, Object> data = new HashMap<>();
            data.put("return", toJson(returnRequest));
            if (file != null) {
                data.put("file", file.toPath());
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/other-expense-returns"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(ofMultipartData(data, boundary)).build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            return objectMapper.readValue(response.body(), OtherExpenseReturnResponse.class);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<OtherExpenseReturnResponse> updateExpenseReturn(long id, OtherExpenseReturnRequest returnRequest, File file) {
        try {
            String boundary = "---" + UUID.randomUUID().toString();
            Map<Object, Object> data = new HashMap<>();
            data.put("return", toJson(returnRequest));
            if (file != null) {
                data.put("file", file.toPath());
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/other-expense-returns/" + id))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .PUT(ofMultipartData(data, boundary)).build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            return objectMapper.readValue(response.body(), OtherExpenseReturnResponse.class);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> deleteExpenseReturn(long id) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/other-expense-returns/" + id)).DELETE().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<List<OtherExpenseExchangeResponse>> getAllExchanges() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/other-expense-exchanges")).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<OtherExpenseExchangeResponse> createExchange(OtherExpenseExchangeRequest exchangeRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/other-expense-exchanges"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(exchangeRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<Void> deleteExchange(long id) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/other-expense-exchanges/" + id)).DELETE().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    // --- REPORTING METHODS ---

    public CompletableFuture<List<OtherExpenseResponse>> generateExpenseReport(ReportRequest reportRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/reports/other-expenses"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(reportRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    // --- DEBIT & CREDIT NOTE METHODS ---

    public CompletableFuture<List<DebitNoteResponse>> getAllDebitNotes() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/debit-notes")).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<DebitNoteResponse> createDebitNote(DebitNoteRequest dnRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/debit-notes"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(dnRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<DebitNoteResponse> updateDebitNote(long id, DebitNoteRequest dnRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/debit-notes/" + id))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(dnRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<Void> deleteDebitNote(long id) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/debit-notes/" + id)).DELETE().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<List<CreditNoteResponse>> getAllCreditNotes() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/credit-notes")).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<CreditNoteResponse> createCreditNote(CreditNoteRequest cnRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/credit-notes"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(cnRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<CreditNoteResponse> updateCreditNote(Long id, CreditNoteRequest cnRequest) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/credit-notes/" + id))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(cnRequest))).build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<Void> deleteCreditNote(Long id) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/credit-notes/" + id)).DELETE().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<CreditNoteResponse> addPayment(Long creditNoteId, CreditNotePaymentRequest paymentRequest) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/credit-notes/" + creditNoteId + "/payments"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(paymentRequest)))
                .build();
        return sendAsync(request, new TypeReference<>() {});
    }



    public CompletableFuture<List<CreditNoteFileResponse>> getFilesForCreditNote(long creditNoteId) {
        // ✅ FIX: Corrected the URL to match a standard REST pattern for the controller.
        String endpoint = BASE_URL + "/credit-notes/" + creditNoteId + "/files";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<CreditNoteFileResponse> uploadCreditNoteFile(long creditNoteId, File file) {
        try {
            String boundary = "---" + UUID.randomUUID().toString();


            Map<Object, Object> data = Map.of(
                    "file", file.toPath()
            );

            // The endpoint URL is correct for your updated controller.
            String endpoint = BASE_URL + "/credit-notes/" + creditNoteId + "/files";

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(ofMultipartData(data, boundary)).build();

            // The generic helper will handle the response.
            return sendAsync(request, new TypeReference<>() {});

        } catch (IOException e) {
            // If there's an issue creating the request body, fail the future.
            return CompletableFuture.failedFuture(e);
        }
    }



    public CompletableFuture<List<DebitNoteFileResponse>> getFilesForDebitNote(long debitNoteId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/debit-note-files/debit-note/" + debitNoteId)).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<DebitNoteFileResponse> uploadDebitNoteFile(long debitNoteId, File file, String uploadedBy) {
        try {
            String boundary = "---" + UUID.randomUUID().toString();
            Map<Object, Object> data = Map.of(
                    "debitNoteId", String.valueOf(debitNoteId),
                    "uploadedBy", uploadedBy,
                    "file", file.toPath()
            );

            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/debit-note-files/upload"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(ofMultipartData(data, boundary)).build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            return objectMapper.readValue(response.body(), DebitNoteFileResponse.class);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> deleteDebitNoteFile(long fileId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/debit-note-files/" + fileId)).DELETE().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    public CompletableFuture<List<DebitNoteAuditLogResponse>> getDebitNoteAuditLogs() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/debit-note-audit-logs")).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }

    // --- GENERAL DATA METHODS ---

    public CompletableFuture<List<Customer>> getCustomers() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/customers")).GET().build();
        return sendAsync(request, new TypeReference<>() {});
    }


    // --- SYNCHRONOUS EMPLOYEE & PAYSLIP API METHODS ---

    public List<EmployeeFx> searchEmployees(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format("%s/employees/search?query=%s", BASE_URL, encodedQuery);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public List<PayslipFx> getAllPayslips() {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/payslips")).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public PayslipRequest getCalculatedPayslipData(String employeeCode, String month) {
        try {
            String url = String.format("%s/payslips/calculate?employeeCode=%s&month=%s", BASE_URL, employeeCode, month);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), PayslipRequest.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public HttpResponse<String> savePayslip(PayslipRequest payslipRequest) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/payslips"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(payslipRequest)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private <T> CompletableFuture<T> handleMultipartResponse(HttpResponse<String> response, Class<T> valueType) {
        String responseBody = response.body();

        System.out.println("SERVER RESPONSE JSON: " + responseBody);

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            try {
                return CompletableFuture.completedFuture(objectMapper.readValue(responseBody, valueType));
            } catch (JsonProcessingException e) {
                return CompletableFuture.failedFuture(new RuntimeException("Failed to parse successful JSON response", e));
            }
        } else {
            return CompletableFuture.failedFuture(new RuntimeException("HTTP request failed with status: " + response.statusCode() + " Body: " + responseBody));
        }
    }
    public CompletableFuture<List<CreditNoteAuditLogResponse>> getAllCreditNoteAuditLogs() {
        HttpRequest request = HttpRequest.newBuilder()

                .uri(URI.create(BASE_URL + "/credit-note-audit-logs"))
                .GET()
                .build();
        return sendAsync(request, new TypeReference<>() {});
    }
    public CompletableFuture<BarcodeResponseDTO> generateBarcode(BarcodeGenerationDTO barcodeRequest) {
        String requestBody = toJson(barcodeRequest);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/barcodes/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return sendAsync(request, new TypeReference<BarcodeResponseDTO>() {});
    }


}