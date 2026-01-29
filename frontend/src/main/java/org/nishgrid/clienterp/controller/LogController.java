package org.nishgrid.clienterp.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import org.nishgrid.clienterp.model.PurchaseAuditLog;
import org.nishgrid.clienterp.model.PurchaseStatusLog;
import org.nishgrid.clienterp.service.ApiService;

import java.time.LocalDateTime;

public class LogController {

    private final ApiService apiService = new ApiService();

    // Audit Log Table
    @FXML private TableView<PurchaseAuditLog> auditLogTable;
    @FXML private TableColumn<PurchaseAuditLog, LocalDateTime> colAuditTimestamp;
    @FXML private TableColumn<PurchaseAuditLog, String> colAuditUser;
    @FXML private TableColumn<PurchaseAuditLog, String> colAuditAction;
    @FXML private TableColumn<PurchaseAuditLog, String> colAuditModule;
    @FXML private TableColumn<PurchaseAuditLog, String> colAuditDetails;

    // Status Log Table
    @FXML private TableView<PurchaseStatusLog> statusLogTable;
    @FXML private TableColumn<PurchaseStatusLog, LocalDateTime> colStatusTimestamp;
    @FXML private TableColumn<PurchaseStatusLog, String> colStatusPoNumber;
    @FXML private TableColumn<PurchaseStatusLog, String> colStatusOld;
    @FXML private TableColumn<PurchaseStatusLog, String> colStatusNew;
    @FXML private TableColumn<PurchaseStatusLog, String> colStatusUser;

    @FXML
    public void initialize() {
        setupTables();
        loadLogs();
    }

    private void setupTables() {
        // Audit Log Table Columns
        colAuditTimestamp.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        colAuditUser.setCellValueFactory(new PropertyValueFactory<>("userId"));
        colAuditAction.setCellValueFactory(new PropertyValueFactory<>("actionType"));
        colAuditModule.setCellValueFactory(new PropertyValueFactory<>("module"));
        colAuditDetails.setCellValueFactory(new PropertyValueFactory<>("details"));

        // Status Log Table Columns
        colStatusTimestamp.setCellValueFactory(new PropertyValueFactory<>("changedAt"));
        colStatusPoNumber.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getPurchaseOrder().getPoNumber()));
        colStatusOld.setCellValueFactory(new PropertyValueFactory<>("oldStatus"));
        colStatusNew.setCellValueFactory(new PropertyValueFactory<>("newStatus"));
        colStatusUser.setCellValueFactory(new PropertyValueFactory<>("changedBy"));
    }

    private void loadLogs() {
        apiService.getAuditLogs().thenAccept(logs ->
                Platform.runLater(() -> auditLogTable.setItems(FXCollections.observableArrayList(logs)))
        );

        apiService.getStatusLogs().thenAccept(logs ->
                Platform.runLater(() -> statusLogTable.setItems(FXCollections.observableArrayList(logs)))
        );
    }
}