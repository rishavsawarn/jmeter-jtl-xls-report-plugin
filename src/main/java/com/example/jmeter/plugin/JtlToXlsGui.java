package com.example.jmeter.plugin;

import org.apache.jmeter.visualizers.gui.AbstractListenerGui;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.samplers.Clearable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class JtlToXlsGui extends AbstractListenerGui implements Clearable {

    private JTextField jtlDirectory;
    private JTextField baselineDirectory;
    private JTextField outputDirectory;
    private JTextField environment;
    private JTextField url;
    private JTextField outputFileName;
    private JTextField transactionLabel;   // Enhancement 7: comma-separated prefixes
    private JTextField scriptingName;
    private JTextField slaField;

    private JTextArea    logArea;
    private JProgressBar progressBar;

    public JtlToXlsGui() {
        initGui();
    }

    private void initGui() {
        setLayout(new BorderLayout(10, 10));
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

    // =========================================================================
    // DIRECTORY PANEL
    // Enhancement 1 — Browse accepts both file and directory
    // Enhancement 6 — Pre-fill fields from last used paths
    // =========================================================================
    private JPanel createDirectoryPanel() {

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Directories"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        // Enhancement 6 — restore last used paths
        jtlDirectory      = new JTextField(JMeterAggregateFull.getLastJtlPath(), 30);
        baselineDirectory = new JTextField(JMeterAggregateFull.getLastBaselinePath(), 30);
        outputDirectory   = new JTextField(JMeterAggregateFull.getLastOutputPath(), 30);

        // Enhancement 1 — browse for file OR directory
        JButton browseJtl = new JButton("Browse");
        browseJtl.addActionListener(e -> chooseFileOrDirectory(jtlDirectory));

        JButton browseBaseline = new JButton("Browse");
        browseBaseline.addActionListener(e -> chooseFileOrDirectory(baselineDirectory));

        JButton browseOutput = new JButton("Browse");
        browseOutput.addActionListener(e -> chooseDirectory(outputDirectory));

        // Row 0 — JTL path (file or directory)
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0; gbc.gridwidth = 1;
        panel.add(new JLabel("JTL File / Directory:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(jtlDirectory, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        panel.add(browseJtl, gbc);

        // Row 1 — Baseline path (file or directory, optional)
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        JLabel baselineLabel = new JLabel("Baseline JTL File / Directory (optional):");
        baselineLabel.setForeground(new Color(0, 102, 204));
        panel.add(baselineLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(baselineDirectory, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        panel.add(browseBaseline, gbc);

        // Row 2 — Output Directory
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(new JLabel("Output Directory:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(outputDirectory, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        panel.add(browseOutput, gbc);

        // Row 3 — hint
        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 1; gbc.gridwidth = 2;
        JLabel hint = new JLabel(
            "JTL: accepts a single .jtl file or a folder. Baseline: optional, enables 3-sheet comparison report.");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));
        hint.setForeground(Color.GRAY);
        panel.add(hint, gbc);

        return panel;
    }

    // =========================================================================
    // CONFIG PANEL
    // Enhancement 7 — Transaction Label Prefix hint updated for comma-separated
    // =========================================================================
    private JPanel createConfigPanel() {

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Configuration"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        environment      = new JTextField("PERF", 20);
        url              = new JTextField(20);
        outputFileName   = new JTextField("JMeterReports", 20);
        transactionLabel = new JTextField("PC_", 20);
        scriptingName    = new JTextField("Rishav", 20);
        slaField         = new JTextField("10", 20);

        int y = 0;
        addConfigRow(panel, gbc, y++, "Environment:",              environment);
        addConfigRow(panel, gbc, y++, "Application URL:",          url);
        addConfigRow(panel, gbc, y++, "Output File Name:",         outputFileName);
        // Enhancement 7 — label updated to mention comma-separated
        addConfigRow(panel, gbc, y++, "Transaction Prefix(es) — comma separated (e.g. PC_, API_):", transactionLabel);
        addConfigRow(panel, gbc, y++, "Scripting Name:",           scriptingName);
        addConfigRow(panel, gbc, y++, "P90 SLA (seconds):",        slaField);

        return panel;
    }

    private void addConfigRow(JPanel panel, GridBagConstraints gbc, int y,
                              String label, JTextField field) {
        gbc.gridx = 0; gbc.gridy = y; gbc.weightx = 0; gbc.gridwidth = 1;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(field, gbc);
    }

    // =========================================================================
    // GENERATE / PROGRESS / LOG PANELS
    // =========================================================================
    private JPanel createGeneratePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton generateBtn = new JButton("Generate Report");
        generateBtn.setPreferredSize(new Dimension(180, 35));
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
        logArea = new JTextArea(8, 60);
        logArea.setEditable(false);
        panel.add(new JScrollPane(logArea));
        return panel;
    }

    // =========================================================================
    // Enhancement 1 — Browse for file OR directory
    // =========================================================================
    private void chooseFileOrDirectory(JTextField field) {
        JFileChooser chooser = new JFileChooser();
        // Allow selecting both files and directories
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".jtl");
            }
            @Override public String getDescription() {
                return "JTL Files (*.jtl) or Directories";
            }
        });
        // Pre-fill with last used path if field is empty
        String current = field.getText().trim();
        if (!current.isEmpty()) {
            File currentFile = new File(current);
            chooser.setCurrentDirectory(currentFile.isDirectory() ? currentFile : currentFile.getParentFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    // Output directory — directories only
    private void chooseDirectory(JTextField field) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String current = field.getText().trim();
        if (!current.isEmpty()) {
            File currentFile = new File(current);
            if (currentFile.exists()) chooser.setCurrentDirectory(currentFile);
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void log(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // =========================================================================
    // GENERATE REPORT
    // Enhancement 8 — overwrite confirmation dialog
    // =========================================================================
    private void generateReport() {
        try {
            progressBar.setValue(10);
            log("Starting report generation...");

            String jtlPath     = jtlDirectory.getText().trim();
            String baselinePath = baselineDirectory.getText().trim();
            String outputDir   = outputDirectory.getText().trim();
            String env         = environment.getText();
            String urlVal      = url.getText();
            String outputFile  = outputFileName.getText();
            String txnPrefixes = transactionLabel.getText(); // Enhancement 7: comma-separated
            String scripting   = scriptingName.getText();
            double sla         = Double.parseDouble(slaField.getText());

            // Validate required fields
            if (jtlPath.isEmpty()) {
                log("❌ JTL File / Directory is required.");
                progressBar.setValue(0);
                return;
            }
            if (outputDir.isEmpty()) {
                log("❌ Output Directory is required.");
                progressBar.setValue(0);
                return;
            }

            // Enhancement 8 — overwrite confirmation
            JMeterAggregateFull core = new JMeterAggregateFull();
            if (core.outputFileExists(outputDir, outputFile)) {
                int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Report already exists:\n" + outputDir + File.separator + outputFile + ".xlsx\n\nOverwrite it?",
                    "File Already Exists",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );
                if (choice != JOptionPane.YES_OPTION) {
                    log("⚠️ Report generation cancelled — file not overwritten.");
                    progressBar.setValue(0);
                    return;
                }
            }

            // Log what mode we're running in
            File jtlFile = new File(jtlPath);
            if (jtlFile.isFile()) {
                log("Mode: Single JTL file — " + jtlFile.getName());
            } else {
                log("Mode: Directory — scanning for .jtl files in " + jtlPath);
            }

            if (baselinePath.isEmpty()) {
                log("No baseline provided — generating single-sheet report.");
            } else {
                File bFile = new File(baselinePath);
                log("Baseline: " + (bFile.isFile() ? "Single file — " + bFile.getName() : "Directory — " + baselinePath));
                log("Will generate 3-sheet report: Current Run, Baseline, Comparison.");
            }

            // Enhancement 7 — log prefixes
            if (!txnPrefixes.trim().isEmpty()) {
                log("Prefix filter(s): " + txnPrefixes);
            } else {
                log("No prefix filter — all transactions included.");
            }

            progressBar.setValue(30);
            log("Processing JTL data...");

            core.generateReport(
                    jtlPath,
                    baselinePath,
                    outputDir,
                    env,
                    urlVal,
                    outputFile,
                    txnPrefixes,
                    scripting,
                    sla
            );

            progressBar.setValue(100);
            log("✅ Report generated successfully.");
            log("📁 Saved to: " + outputDir + File.separator + outputFile + ".xlsx");

        } catch (Exception ex) {
            log("❌ Error: " + ex.getMessage());
            progressBar.setValue(0);
        }
    }

    // =========================================================================
    // JMETER LISTENER INTERFACE
    // =========================================================================
    @Override public String getStaticLabel() { return "JTL XLS Smart Report Generator"; }
    @Override public String getLabelResource() { return null; }

    @Override
    public TestElement createTestElement() {
        JtlToXlsListener element = new JtlToXlsListener();
        modifyTestElement(element);
        return element;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        super.modifyTestElement(element);
        element.setProperty("jtlDirectory",     jtlDirectory.getText());
        element.setProperty("baselineDirectory", baselineDirectory.getText());
        element.setProperty("outputDirectory",   outputDirectory.getText());
        element.setProperty("environment",       environment.getText());
        element.setProperty("url",               url.getText());
        element.setProperty("outputFileName",    outputFileName.getText());
        element.setProperty("transactionLabel",  transactionLabel.getText());
        element.setProperty("scriptingName",     scriptingName.getText());
        element.setProperty("slaValue",          slaField.getText());
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        jtlDirectory.setText(element.getPropertyAsString("jtlDirectory"));
        baselineDirectory.setText(element.getPropertyAsString("baselineDirectory"));
        outputDirectory.setText(element.getPropertyAsString("outputDirectory"));
        environment.setText(element.getPropertyAsString("environment"));
        url.setText(element.getPropertyAsString("url"));
        outputFileName.setText(element.getPropertyAsString("outputFileName"));
        transactionLabel.setText(element.getPropertyAsString("transactionLabel"));
        scriptingName.setText(element.getPropertyAsString("scriptingName"));
        slaField.setText(element.getPropertyAsString("slaValue", "10"));
    }

    // =========================================================================
    // CLEAR METHODS
    // Clear     → clearData()  — clears log + progress only
    // Clear All → clearGui()   — resets all fields + log + progress
    // =========================================================================
    @Override
    public void clearGui() {
        super.clearGui();
        jtlDirectory.setText("");
        baselineDirectory.setText("");
        outputDirectory.setText("");
        environment.setText("PERF");
        url.setText("");
        outputFileName.setText("JMeterReports");
        transactionLabel.setText("PC_");
        scriptingName.setText("");
        slaField.setText("10");
        resetLogAndProgress();
    }

    @Override
    public void clearData() {
        resetLogAndProgress();
    }

    private void resetLogAndProgress() {
        if (logArea     != null) logArea.setText("");
        if (progressBar != null) progressBar.setValue(0);
    }
}
