package org.nishgrid.clienterp.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.nishgrid.clienterp.dto.CreditNotePaymentRequest;
import org.nishgrid.clienterp.model.CreditNotePayment;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AddPaymentDialogController {

    @FXML private ComboBox<CreditNotePayment.SettlementType> settlementTypeComboBox;
    @FXML private TextField amountField;
    @FXML private DatePicker settlementDatePicker;
    @FXML private TextField referenceField;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private Stage dialogStage;
    private CreditNotePaymentRequest paymentResult;
    private boolean okClicked = false;

    @FXML
    private void initialize() {
        settlementTypeComboBox.setItems(FXCollections.observableArrayList(CreditNotePayment.SettlementType.values()));
        settlementDatePicker.setValue(LocalDate.now());
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public boolean isOkClicked() {
        return okClicked;
    }

    public CreditNotePaymentRequest getPaymentResult() {
        return paymentResult;
    }

    @FXML
    private void handleSave() {
        if (isInputValid()) {
            paymentResult = new CreditNotePaymentRequest();
            paymentResult.setSettlementType(settlementTypeComboBox.getValue());
            paymentResult.setAmount(new BigDecimal(amountField.getText()));
            paymentResult.setSettlementDate(settlementDatePicker.getValue());
            paymentResult.setReferenceNumber(referenceField.getText());

            okClicked = true;
            dialogStage.close();
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    private boolean isInputValid() {
        // Add validation logic here if needed
        return true;
    }
}