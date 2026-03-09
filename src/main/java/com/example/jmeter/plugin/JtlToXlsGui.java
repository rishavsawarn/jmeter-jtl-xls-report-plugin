package com.example.jmeter.plugin;

import org.apache.jmeter.visualizers.gui.AbstractListenerGui;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.samplers.Clearable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class JtlToXlsGui extends AbstractListenerGui implements Clearable {

    private JTextField jtlDirectory;
    private JTextField outputDirectory;
    private JTextField environment;
    private JTextField url;
    private JTextField outputFileName;
    private JTextField transactionLabel;
    private JTextField scriptingName;
    private JTextField slaField;

    private JTextArea logArea;
    private JProgressBar progressBar;

    public JtlToXlsGui() {
        initGui();
    }

    private void initGui() {

        setLayout(new BorderLayout(10,10));
        setBorder(makeBorder());

        add(makeTitlePanel(), BorderLayout.NORTH);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        mainPanel.add(createDirectoryPanel());
        mainPanel.add(createConfigPanel());
        mainPanel.add(createGeneratePanel());
        mainPanel.add(createProgressPanel());
        mainPanel.add(createLogPanel());

        add(mainPanel, BorderLayout.CENTER);
    }

    private JPanel createDirectoryPanel() {

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Directories"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        jtlDirectory = new JTextField(30);
        JButton browseJtl = new JButton("Browse");
        browseJtl.addActionListener(e -> chooseDirectory(jtlDirectory));

        outputDirectory = new JTextField(30);
        JButton browseOutput = new JButton("Browse");
        browseOutput.addActionListener(e -> chooseDirectory(outputDirectory));

        gbc.gridx=0; gbc.gridy=0;
        panel.add(new JLabel("JTL Directory:"), gbc);

        gbc.gridx=1;
        panel.add(jtlDirectory, gbc);

        gbc.gridx=2;
        panel.add(browseJtl, gbc);

        gbc.gridx=0; gbc.gridy=1;
        panel.add(new JLabel("Output Directory:"), gbc);

        gbc.gridx=1;
        panel.add(outputDirectory, gbc);

        gbc.gridx=2;
        panel.add(browseOutput, gbc);

        return panel;
    }

    private JPanel createConfigPanel() {

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Configuration"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        environment = new JTextField("PERF",20);
        url = new JTextField(20);
        outputFileName = new JTextField("JMeterReports",20);
        transactionLabel = new JTextField("PC_",20);
        scriptingName = new JTextField("Rishav",20);
        slaField = new JTextField("10",20);

        int y = 0;

        addConfigRow(panel, gbc, y++, "Environment:", environment);
        addConfigRow(panel, gbc, y++, "Application URL:", url);
        addConfigRow(panel, gbc, y++, "Output File Name:", outputFileName);
        addConfigRow(panel, gbc, y++, "Transaction Label Prefix:", transactionLabel);
        addConfigRow(panel, gbc, y++, "Scripting Name:", scriptingName);
        addConfigRow(panel, gbc, y++, "P90 SLA (ms):", slaField);

        return panel;
    }

    private void addConfigRow(JPanel panel, GridBagConstraints gbc, int y,
                              String label, JTextField field) {

        gbc.gridx = 0;
        gbc.gridy = y;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    private JPanel createGeneratePanel() {

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        JButton generateBtn = new JButton("Generate Report");
        generateBtn.setPreferredSize(new Dimension(180,35));

        generateBtn.addActionListener(e -> generateReport());

        panel.add(generateBtn);

        return panel;
    }

    private JPanel createProgressPanel() {

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Progress"));

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);

        panel.add(progressBar);

        return panel;
    }

    private JPanel createLogPanel() {

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Status Logs"));

        logArea = new JTextArea(8,60);
        logArea.setEditable(false);

        JScrollPane scroll = new JScrollPane(logArea);

        panel.add(scroll);

        return panel;
    }

    private void chooseDirectory(JTextField field) {

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = chooser.showOpenDialog(this);

        if(result == JFileChooser.APPROVE_OPTION){
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    /* Khud se SCROLL(Auto) */

    private void log(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void generateReport() {

        try {

            progressBar.setValue(10);
            log("Starting report generation...");

            String jtlDir = jtlDirectory.getText();
            String outputDir = outputDirectory.getText();
            String env = environment.getText();
            String urlVal = url.getText();
            String outputFile = outputFileName.getText();
            String txn = transactionLabel.getText();
            String scripting = scriptingName.getText();

            double sla = Double.parseDouble(slaField.getText());

            log("Scanning JTL directory...");
            progressBar.setValue(40);

            new JMeterAggregateFull().generateReport(
                    jtlDir,
                    outputDir,
                    env,
                    urlVal,
                    outputFile,
                    txn,
                    scripting,
                    sla
            );

            progressBar.setValue(100);
            log("Report generated successfully.");

        } catch (Exception ex) {

            log(" Error generating report:");
            log(ex.getMessage());
            progressBar.setValue(0);
        }
    }

    @Override
    public String getStaticLabel() {
        return "JTL XLS Smart Report Generator";
    }

    @Override
    public String getLabelResource() {
        return null;
    }

    @Override
    public TestElement createTestElement() {

        JtlToXlsListener element = new JtlToXlsListener();
        modifyTestElement(element);
        return element;
    }

    @Override
    public void modifyTestElement(TestElement element) {

        super.modifyTestElement(element);

        element.setProperty("jtlDirectory", jtlDirectory.getText());
        element.setProperty("outputDirectory", outputDirectory.getText());
        element.setProperty("environment", environment.getText());
        element.setProperty("url", url.getText());
        element.setProperty("outputFileName", outputFileName.getText());
        element.setProperty("transactionLabel", transactionLabel.getText());
        element.setProperty("scriptingName", scriptingName.getText());
        element.setProperty("slaValue", slaField.getText());
    }

    @Override
    public void configure(TestElement element) {

        super.configure(element);

        jtlDirectory.setText(element.getPropertyAsString("jtlDirectory"));
        outputDirectory.setText(element.getPropertyAsString("outputDirectory"));
        environment.setText(element.getPropertyAsString("environment"));
        url.setText(element.getPropertyAsString("url"));
        outputFileName.setText(element.getPropertyAsString("outputFileName"));
        transactionLabel.setText(element.getPropertyAsString("transactionLabel"));
        scriptingName.setText(element.getPropertyAsString("scriptingName"));
        slaField.setText(element.getPropertyAsString("slaValue","10"));
    }

    @Override
    public void clearGui() {

        super.clearGui();

        jtlDirectory.setText("");
        outputDirectory.setText("");
        environment.setText("PERF");
        url.setText("");
        outputFileName.setText("JMeterReports");
        transactionLabel.setText("PC_");
        scriptingName.setText("");
        slaField.setText("10");
    }


    @Override
    public void clearData() {

        if (logArea != null) {
            logArea.setText("");
        }

        if (progressBar != null) {
            progressBar.setValue(0);
        }
    }
}