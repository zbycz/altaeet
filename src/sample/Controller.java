package sample;

import cz.etrzby.xml.TrzbaDataType;
import cz.tomasdvorak.eet.client.EETClient;
import cz.tomasdvorak.eet.client.EETServiceFactory;
import cz.tomasdvorak.eet.client.config.CommunicationMode;
import cz.tomasdvorak.eet.client.config.EndpointType;
import cz.tomasdvorak.eet.client.config.SubmissionType;
import cz.tomasdvorak.eet.client.dto.SubmitResult;
import cz.tomasdvorak.eet.client.exceptions.CommunicationException;
import cz.tomasdvorak.eet.client.exceptions.CommunicationTimeoutException;
import cz.tomasdvorak.eet.client.exceptions.DataSigningException;
import cz.tomasdvorak.eet.client.exceptions.InvalidKeystoreException;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.Scanner;


public class Controller {
    @FXML
    private TextArea historyTextArea;

    @FXML
    private TextField amountTextField;

    @FXML
    private Label provozovnaLabel;

    private String posledniUctenka = "Zatím neproběhla platba.";

    @FXML
    public void initialize() {
        getIdProvoz();
        String poradCis = getPoradCis(false);
        log("Program spuštěn. Pořadové číslo poslední účtenky: "+poradCis);
    }

    @FXML
    public void copyCliboard() {
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent content = new ClipboardContent();
        content.putString(posledniUctenka);
        clipboard.setContent(content);
    }

    @FXML
    public void showHistory() {
        try {
            java.awt.Desktop.getDesktop().edit(new File("eetlog.txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleButtonClick(ActionEvent event) {
        try {
            BigDecimal amount = getAmount();
            Date date = new Date();
            String poradCis = getPoradCis(true);
            int idProvoz = getIdProvoz();

            log("\r\n-------------------------------");
            posledniUctenka = "";
            logUctenka("Číslo: " + poradCis +" (provozovna "+idProvoz+")\r\nDatum: " + date + "\r\nČástka: " + amount);

            InputStream clientKey = getClass().getResourceAsStream("/keys/CZ48034398.p12");
            InputStream rootCACertificate = getClass().getResourceAsStream("/keys/rca15_rsa.der");
            InputStream subordinateCACertificate = getClass().getResourceAsStream("/keys/2qca16_rsa.der");
            EETClient client = EETServiceFactory.getInstance(clientKey, "eet", rootCACertificate, subordinateCACertificate);

            TrzbaDataType data = new TrzbaDataType()
                    .withDicPopl("CZ48034398") //alta
                    .withIdProvoz(idProvoz)
                    .withIdPokl("POKL1")
                    .withPoradCis(poradCis)
                    .withDatTrzby(date)
                    .withCelkTrzba(amount);

            SubmitResult result = client.submitReceipt(data, CommunicationMode.REAL, EndpointType.PRODUCTION, SubmissionType.FIRST_ATTEMPT);
            // print codes on the receipt
            logUctenka("FIK:" + result.getFik()
                    + "\r\nBKP:" + result.getBKP()
                    + "\r\nTržba je evidována v běžném režimu.");

        } catch (final CommunicationException e) {
            logUctenka("Odeslání se nezdařilo, na účtenku vložte náhradní offline kód níže."
                    + "\r\nOdeslání opakujte až bude systém online (nejpozději do 48h) !!"
                    + "\r\nPKP:" + e.getPKP()
                    + "\r\nBKP:" + e.getBKP()
                    + "\r\n"
                    + e.getRequest().getData().getDatTrzby().toString()
                    + "\r\nTržba je evidována v běžném režimu.");

        } catch (DataSigningException e) {
            log("Závažná chyba - data nebyla odeslána\r\n");
            log(e.getClass().getName() + ":" + e.getMessage());
            e.printStackTrace();

        } catch (InvalidKeystoreException e) {
            log("Závažná chyba - data nebyla odeslána");
            log(e.getClass().getName() + ":" + e.getMessage());
            e.printStackTrace();

        } catch (RuntimeException e) {
            log(e.getMessage());
        }

        log("KONEC");
    }

    private int getIdProvoz() {
        Scanner in = null;
        try {
            in = new Scanner(new FileReader("provozovna.txt"));
        } catch (FileNotFoundException e) {
            provozovnaLabel.setText("provozovna.txt nenalezeno !!!");
            throw new RuntimeException("provozovna.txt nenalezeno !!!");
        }

        int id = in.nextInt();
        String s = in.next();
        provozovnaLabel.setText("("+id + " " + s + ")");
        return id;
    }

    private String getPoradCis(boolean increment) {
        Scanner in = null;
        try {
            in = new Scanner(new FileReader("poradovecislo.txt"));

            int i = in.nextInt();

            if (increment) {
                i++;

                PrintWriter writer = new PrintWriter("poradovecislo.txt", "UTF-8");
                writer.println(i);
                writer.close();
            }
            return i + "";

        } catch (FileNotFoundException e) {
            throw new RuntimeException("poradovecislo.txt nenalezeno !!!");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new RuntimeException("UTF-8 missing");
        }
    }


    private BigDecimal getAmount() {
        String amountTxt = amountTextField.getText();
        amountTextField.setText("");
        amountTextField.commitValue();
        try {
            amountTxt = amountTxt.replace(',', '.').replaceAll("[^-.0-9]+", "");
            return new BigDecimal(amountTxt);
        } catch (Exception e) {
            throw new RuntimeException("Částka není číselná. KONEC");
        }
    }


    void log(String s) {
        s += "\r\n";
        historyTextArea.setText(historyTextArea.getText() + s);
        historyTextArea.commitValue();

        try {
            Files.write(Paths.get("eetlog.txt"), s.getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void logUctenka(String s) {
        posledniUctenka += s + "\r\n";
        log(s);
    }
}
