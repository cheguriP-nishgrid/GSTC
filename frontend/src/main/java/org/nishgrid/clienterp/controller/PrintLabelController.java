package org.nishgrid.clienterp.controller;

import javafx.fxml.FXML;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.nishgrid.clienterp.dto.BarcodeResponseDTO;
import org.nishgrid.clienterp.util.BarcodeGenerator;


public class PrintLabelController {

    @FXML private VBox printableArea;
    @FXML private Label productNameLabel;
    @FXML private ImageView barcodeImageView;
    @FXML private Label barcodeValueLabel;
    @FXML private Button printButton;


    public void populateLabel(BarcodeResponseDTO barcodeData) {
        productNameLabel.setText(barcodeData.getProductName());
        barcodeValueLabel.setText(barcodeData.getBarcodeValue());


        barcodeImageView.setImage(BarcodeGenerator.generateBarcodeImage(barcodeData.getBarcodeValue(), 250, 60));
    }

    @FXML
    private void handlePrint() {

        printButton.setVisible(false);

        System.out.println("Attempting to create a printer job...");


        PrinterJob job = PrinterJob.createPrinterJob();

        if (job == null) {
            System.err.println("Could not create a printer job. Check that a printer is installed and configured.");

            printButton.setVisible(true);
            return;
        }

        System.out.println("Printer job created successfully.");

        if (job.showPrintDialog(printableArea.getScene().getWindow())) {
            System.out.println("Print dialog confirmed. Proceeding to print.");
            boolean success = job.printPage(printableArea);
            if (success) {
                System.out.println("Printing was successful.");
                job.endJob();

                Stage stage = (Stage) printableArea.getScene().getWindow();
                stage.close();
            } else {
                System.err.println("Printing failed.");
            }
        } else {
            System.out.println("Print dialog was cancelled by the user.");
        }


        printButton.setVisible(true);
    }
}