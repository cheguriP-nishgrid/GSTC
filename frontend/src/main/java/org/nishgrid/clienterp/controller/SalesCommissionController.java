package org.nishgrid.clienterp.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import org.nishgrid.clienterp.dto.EmployeeSelectionDTO;
import org.nishgrid.clienterp.dto.InvoiceSelectionDTO;
import org.nishgrid.clienterp.dto.SalesCommissionDTO;
import org.nishgrid.clienterp.model.SalesCommission;
import org.nishgrid.clienterp.service.ApiService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Collectors;

public class SalesCommissionController {

    @FXML private TextField rateField, amountField;
    @FXML private ComboBox<EmployeeSelectionDTO> employeeComboBox;
    @FXML private ComboBox<InvoiceSelectionDTO> invoiceComboBox;
    @FXML private TableView<SalesCommission> commissionTable;
    @FXML private TableColumn<SalesCommission, Number> commissionIdCol;
    @FXML private TableColumn<SalesCommission, String> employeeNameCol, invoiceNoCol, rateCol;
    @FXML private TableColumn<SalesCommission, Number> amountCol;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String API_BASE_URL = ApiService.getBaseUrl() + "/commissions";
    private final String EMPLOYEES_API_URL = ApiService.getBaseUrl() + "/employees/selection";
    private final String INVOICES_API_URL = ApiService.getBaseUrl() + "/invoices/selection";

    @FXML
    public void initialize() {
        commissionIdCol.setCellValueFactory(data -> data.getValue().commissionIdProperty());
        employeeNameCol.setCellValueFactory(data -> data.getValue().employeeNameProperty());
        invoiceNoCol.setCellValueFactory(data -> data.getValue().invoiceNoProperty());
        rateCol.setCellValueFactory(data -> data.getValue().commissionRateProperty());
        amountCol.setCellValueFactory(data -> data.getValue().amountTotalProperty());

        commissionTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        populateForm(newSelection);
                    }
                });

        configureComboBoxes();
        loadEmployees();
        loadInvoices();
        loadCommissions();
    }

    private void configureComboBoxes() {
        employeeComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(EmployeeSelectionDTO employee) {
                return employee == null ? "" : employee.getName();
            }
            @Override
            public EmployeeSelectionDTO fromString(String string) { return null; }
        });

        invoiceComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(InvoiceSelectionDTO invoice) {
                return invoice == null ? "" : invoice.getInvoiceNo();
            }
            @Override
            public InvoiceSelectionDTO fromString(String string) { return null; }
        });
    }

    private void loadEmployees() {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(EMPLOYEES_API_URL)).GET().build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(body -> {
                    try {
                        List<EmployeeSelectionDTO> employees = objectMapper.readValue(body, new TypeReference<>() {});
                        Platform.runLater(() -> employeeComboBox.setItems(FXCollections.observableArrayList(employees)));
                    } catch (Exception e) { e.printStackTrace(); }
                });
    }

    private void loadInvoices() {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(INVOICES_API_URL)).GET().build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(body -> {
                    try {
                        List<InvoiceSelectionDTO> invoices = objectMapper.readValue(body, new TypeReference<>() {});
                        Platform.runLater(() -> invoiceComboBox.setItems(FXCollections.observableArrayList(invoices)));
                    } catch (Exception e) { e.printStackTrace(); }
                });
    }

    private void loadCommissions() {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_BASE_URL)).GET().build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(body -> {
                    try {
                        List<SalesCommissionDTO> dtoList = objectMapper.readValue(body, new TypeReference<>() {});
                        List<SalesCommission> fxList = dtoList.stream().map(this::dtoToFx).collect(Collectors.toList());
                        Platform.runLater(() -> {
                            commissionTable.setItems(FXCollections.observableArrayList(fxList));
                            clearForm();
                        });
                    } catch (Exception e) { e.printStackTrace(); }
                });
    }

    @FXML
    private void handleAdd() {
        try {
            SalesCommissionDTO dto = createDtoFromForm();
            if (dto == null) return;
            String body = objectMapper.writeValueAsString(dto);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_BASE_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            sendRequestAndRefresh(request, "Commission added successfully!");
        } catch (Exception e) {
            showAlert("Error", "Failed to add commission: " + e.getMessage());
        }
    }

    @FXML
    private void handleEdit() {
        SalesCommission selected = commissionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Warning", "Please select a record to update.");
            return;
        }
        try {
            SalesCommissionDTO dto = createDtoFromForm();
            if (dto == null) return;
            String body = objectMapper.writeValueAsString(dto);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_BASE_URL + "/" + selected.getCommissionId()))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
            sendRequestAndRefresh(request, "Commission updated successfully!");
        } catch (Exception e) {
            showAlert("Error", "Failed to update commission: " + e.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        SalesCommission selected = commissionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Warning", "Please select a record to delete.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Delete commission record " + selected.getCommissionId() + "?", ButtonType.YES, ButtonType.NO);
        if (confirmation.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_BASE_URL + "/" + selected.getCommissionId())).DELETE().build();
            sendRequestAndRefresh(request, "Commission deleted successfully!");
        }
    }

    private void sendRequestAndRefresh(HttpRequest request, String successMessage) {
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> Platform.runLater(() -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        showAlert("Success", successMessage);
                        loadCommissions();
                    } else {
                        showAlert("Request Failed", "Server responded with status " + response.statusCode() + ":\n" + response.body());
                    }
                }));
    }

    private SalesCommissionDTO createDtoFromForm() {
        EmployeeSelectionDTO selectedEmployee = employeeComboBox.getSelectionModel().getSelectedItem();
        InvoiceSelectionDTO selectedInvoice = invoiceComboBox.getSelectionModel().getSelectedItem();

        if (selectedEmployee == null) {
            showAlert("Invalid Input", "Please select an employee.");
            return null;
        }
        if (selectedInvoice == null) {
            showAlert("Invalid Input", "Please select an invoice.");
            return null;
        }

        try {
            SalesCommissionDTO dto = new SalesCommissionDTO();
            dto.setEmployeeId(selectedEmployee.getId());
            dto.setInvoiceId(selectedInvoice.getId());
            dto.setCommissionRate(rateField.getText());
            dto.setAmountTotal(Double.parseDouble(amountField.getText()));
            return dto;
        } catch (NumberFormatException e) {
            showAlert("Invalid Input", "Amount must be a valid number.");
            return null;
        }
    }

    private void populateForm(SalesCommission commission) {
        findAndSelectItemEmployee(employeeComboBox, commission.getEmployeeId());
        findAndSelectItemInvoice(invoiceComboBox, commission.getInvoiceId());
        rateField.setText(commission.commissionRateProperty().get());
        amountField.setText(String.valueOf(commission.amountTotalProperty().get()));
    }

    private void findAndSelectItemEmployee(ComboBox<EmployeeSelectionDTO> comboBox, Integer id) {
        comboBox.getItems().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .ifPresent(comboBox.getSelectionModel()::select);
    }

    private void findAndSelectItemInvoice(ComboBox<InvoiceSelectionDTO> comboBox, Integer id) {
        comboBox.getItems().stream()
                .filter(item -> item.getId().equals(Long.valueOf(id)))
                .findFirst()
                .ifPresent(comboBox.getSelectionModel()::select);
    }

    private void clearForm() {
        commissionTable.getSelectionModel().clearSelection();
        employeeComboBox.getSelectionModel().clearSelection();
        invoiceComboBox.getSelectionModel().clearSelection();
        rateField.clear();
        amountField.clear();
    }

    private void showAlert(String title, String content) {
        Alert.AlertType type = title.equals("Success") ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING;
        if(title.equals("Error")) type = Alert.AlertType.ERROR;

        Alert alert = new Alert(type, content);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private SalesCommission dtoToFx(SalesCommissionDTO dto) {
        return new SalesCommission(dto.getCommissionId(), dto.getEmployeeName(),
                dto.getEmployeeId(), dto.getInvoiceNo(), Math.toIntExact(dto.getInvoiceId()),
                dto.getCommissionRate(), dto.getAmountTotal());
    }
}