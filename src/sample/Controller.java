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
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
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
    private String idPokl = null;
    private int idProvoz;
    private String idProvozTxt;

    @FXML
    public void initialize() {
        Scanner in = null;
        try {
            in = new Scanner(new FileReader("nastaveni.txt"));

            if (!in.next().equals("idProvozovny")) throw new RuntimeException("nastaveni.txt - chyba");
            idProvoz = in.nextInt();
            idProvozTxt = in.next();
            if (!in.next().equals("idPokladny")) throw new RuntimeException("nastaveni.txt - chyba");
            idPokl = in.next();

        } catch (Exception e) {
            log(e.getMessage() + " chyba: " + e.getClass().getSimpleName());
            e.printStackTrace();
            return;
        }

        provozovnaLabel.setText("(" + idProvoz + " " + idProvozTxt + ", pokladna " + idPokl + ")");
        log("Program spuštěn pro " + idProvoz + " " + idProvozTxt + ", pokladna " + idPokl + ". \r\nPořadové číslo poslední účtenky: " + getPoradCis(false));
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
        if (idPokl == null) {
            log("Upravte nastaveni.txt a restartujte program. KONEC");
            return;
        }
        try {
            BigDecimal amount = getAmount();
            Date date = new Date();
            String poradCis = getPoradCis(true);


            log("\r\n-------------------------------");
            posledniUctenka = "";
            logUctenka("Číslo: " + poradCis + " (provozovna " + idProvoz + ", pokladna " + idPokl + ")\r\nDatum: " + new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(date) + "\r\nČástka: " + amount);

            InputStream clientKey = getClass().getResourceAsStream("/keys/CZ48034398.p12");
            InputStream rootCACertificate = getClass().getResourceAsStream("/keys/rca15_rsa.der");
            InputStream subordinateCACertificate = getClass().getResourceAsStream("/keys/2qca16_rsa.der");
            EETClient client = EETServiceFactory.getInstance(clientKey, "eet", rootCACertificate, subordinateCACertificate);

            TrzbaDataType data = new TrzbaDataType()
                    .withDicPopl("CZ48034398") //alta
                    .withIdProvoz(idProvoz)
                    .withIdPokl(idPokl)
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

        } catch (Throwable e) {
            log(e.getMessage() + " chyba: " + e.getClass().getSimpleName());
        }

        log("KONEC");
    }

    // ------------------------------------- helpers -------------------------------------

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
