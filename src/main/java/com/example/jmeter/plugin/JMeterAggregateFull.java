package com.example.jmeter.plugin;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

public class JMeterAggregateFull {

    // =========================================================================
    // CLI ENTRY POINT — for headless / GitHub Actions usage
    // =========================================================================
    public static void main(String[] args) {
        try {
            Map<String, String> params = new HashMap<>();

            // Parse --key value pairs
            for (int i = 0; i < args.length - 1; i += 2) {
                params.put(args[i], args[i + 1]);
            }

            String jtlDirectory      = params.getOrDefault("--jtl-dir",      ".");
            String outputDirectory   = params.getOrDefault("--output-dir",   ".");
            String environment       = params.getOrDefault("--env",          "CI");
            String url               = params.getOrDefault("--app",          "N/A");
            String outputFileName    = params.getOrDefault("--output-name",  "JMeterReport");
            String transactionPrefix = params.getOrDefault("--prefix",       "");
            String scriptingName     = params.getOrDefault("--scripted-by",  "CI");

            // SLA is in SECONDS — because generateReport() compares: p90 / 1000.0 > slaSeconds
            // Example: --sla-seconds 10  means SLA = 10 seconds (10000 ms)
            double slaSeconds = Double.parseDouble(
                                    params.getOrDefault("--sla-seconds", "10"));

            System.out.println("=========================================");
            System.out.println("  JTL to Excel Report — Headless Mode   ");
            System.out.println("=========================================");
            System.out.println("JTL Directory    : " + jtlDirectory);
            System.out.println("Output Directory : " + outputDirectory);
            System.out.println("Output File Name : " + outputFileName + ".xlsx");
            System.out.println("Environment      : " + environment);
            System.out.println("Application      : " + url);
            System.out.println("Prefix Filter    : " + (transactionPrefix.isEmpty() ? "(none)" : transactionPrefix));
            System.out.println("Scripted By      : " + scriptingName);
            System.out.println("SLA (seconds)    : " + slaSeconds);
            System.out.println("=========================================");

            new JMeterAggregateFull().generateReport(
                    jtlDirectory,
                    outputDirectory,
                    environment,
                    url,
                    outputFileName,
                    transactionPrefix,
                    scriptingName,
                    slaSeconds
            );

            System.out.println("Done. ✅");

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);   // non-zero exit so GitHub Actions marks the step as failed
        }
    }

    // =========================================================================
    // CORE REPORT GENERATION — called from both GUI and CLI
    // =========================================================================
    public void generateReport(
            String jtlDirectory,
            String outputDirectory,
            String environment,
            String url,
            String outputFileName,
            String transactionPrefix,
            String scriptingName,
            double slaSeconds
    ) throws Exception {

        File dir = new File(jtlDirectory);

        if (!dir.exists()) {
            throw new RuntimeException("JTL directory not found: " + jtlDirectory);
        }

        File[] jtlFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jtl"));

        if (jtlFiles == null || jtlFiles.length == 0) {
            throw new RuntimeException("No JTL files found in: " + jtlDirectory);
        }

        System.out.println("Found " + jtlFiles.length + " JTL file(s) to process.");

        Map<String, List<Long>> responseTimes = new HashMap<>();
        Map<String, Integer> errorCount = new HashMap<>();

        for (File jtl : jtlFiles) {
            System.out.println("Parsing: " + jtl.getName());
            parseJTL(jtl, responseTimes, errorCount, transactionPrefix);
        }

        if (responseTimes.isEmpty()) {
            throw new RuntimeException(
                "No matching transactions found. " +
                "Check your --prefix value (current: '" + transactionPrefix + "')."
            );
        }

        // Ensure output directory exists
        new File(outputDirectory).mkdirs();

        String outputPath = outputDirectory + File.separator + outputFileName + ".xlsx";

        generateExcel(
                responseTimes,
                errorCount,
                outputPath,
                environment,
                url,
                scriptingName,
                slaSeconds
        );
    }

    // =========================================================================
    // JTL PARSING
    // =========================================================================
    private void parseJTL(
            File jtlFile,
            Map<String, List<Long>> responseTimes,
            Map<String, Integer> errorCount,
            String prefix
    ) throws Exception {

        BufferedReader br = new BufferedReader(new FileReader(jtlFile));

        String line;
        boolean firstLine = true;

        while ((line = br.readLine()) != null) {

            if (firstLine) {
                firstLine = false;
                continue; // skip header row
            }

            String[] cols = line.split(",");

            if (cols.length < 8) continue;

            String label = cols[2].trim();

            // If prefix is empty, include all transactions; otherwise filter
            if (!prefix.isEmpty() && !label.startsWith(prefix)) continue;

            long elapsed = Long.parseLong(cols[1].trim());
            boolean success = Boolean.parseBoolean(cols[7].trim());

            responseTimes.putIfAbsent(label, new ArrayList<>());
            responseTimes.get(label).add(elapsed);

            if (!success) {
                errorCount.put(label, errorCount.getOrDefault(label, 0) + 1);
            }
        }

        br.close();
    }

    // =========================================================================
    // EXCEL GENERATION
    // =========================================================================
    private void generateExcel(
            Map<String, List<Long>> responseTimes,
            Map<String, Integer> errors,
            String outputPath,
            String env,
            String url,
            String scriptingName,
            double slaSeconds
    ) throws Exception {

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("JMeter Report");

        Font boldFont = workbook.createFont();
        boldFont.setBold(true);

        /* HEADER STYLE */
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(boldFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        /* NORMAL CELL STYLE */
        CellStyle centerStyle = workbook.createCellStyle();
        centerStyle.setAlignment(HorizontalAlignment.CENTER);
        centerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        centerStyle.setBorderTop(BorderStyle.THIN);
        centerStyle.setBorderBottom(BorderStyle.THIN);
        centerStyle.setBorderLeft(BorderStyle.THIN);
        centerStyle.setBorderRight(BorderStyle.THIN);

        /* SLA BREACH STYLE — red highlight */
        CellStyle redStyle = workbook.createCellStyle();
        redStyle.cloneStyleFrom(centerStyle);
        redStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
        redStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        int rowNum = 0;

        // Title row
        Row title = sheet.createRow(rowNum++);
        title.createCell(0).setCellValue("JMeter Performance Report");

        // Info row
        Row info = sheet.createRow(rowNum++);
        info.createCell(0).setCellValue(
                "Environment: " + env +
                " | URL: " + url +
                " | Scripted By: " + scriptingName +
                " | SLA: " + slaSeconds + "s (P90)"
        );

        rowNum++; // blank row before table

        // Column headers
        String[] headers = {
                "Transaction",
                "Samples",
                "Average(ms)",
                "P90(ms)",
                "P95(ms)",
                "Min(ms)",
                "Max(ms)",
                "Error %"
        };

        Row headerRow = sheet.createRow(rowNum++);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        // Data rows — sorted alphabetically by transaction name
        List<String> transactions = new ArrayList<>(responseTimes.keySet());
        Collections.sort(transactions);

        for (String txn : transactions) {

            List<Long> times = responseTimes.get(txn);
            Collections.sort(times);

            int samples = times.size();
            int error = errors.getOrDefault(txn, 0);

            double avg          = times.stream().mapToLong(Long::longValue).average().orElse(0);
            long   min          = times.get(0);
            long   max          = times.get(times.size() - 1);
            long   p90          = percentile(times, 90);
            long   p95          = percentile(times, 95);
            double errorPercent = ((double) error / samples) * 100;

            Row row = sheet.createRow(rowNum++);

            Cell c0 = row.createCell(0);
            c0.setCellValue(txn);
            c0.setCellStyle(centerStyle);

            Cell c1 = row.createCell(1);
            c1.setCellValue(samples);
            c1.setCellStyle(centerStyle);

            Cell c2 = row.createCell(2);
            c2.setCellValue(Math.round(avg));
            c2.setCellStyle(centerStyle);

            // P90 — highlight red if SLA breached
            Cell c3 = row.createCell(3);
            c3.setCellValue(p90);
            c3.setCellStyle(p90 / 1000.0 > slaSeconds ? redStyle : centerStyle);

            Cell c4 = row.createCell(4);
            c4.setCellValue(p95);
            c4.setCellStyle(centerStyle);

            Cell c5 = row.createCell(5);
            c5.setCellValue(min);
            c5.setCellStyle(centerStyle);

            Cell c6 = row.createCell(6);
            c6.setCellValue(max);
            c6.setCellStyle(centerStyle);

            Cell c7 = row.createCell(7);
            c7.setCellValue(String.format("%.2f%%", errorPercent));
            c7.setCellStyle(centerStyle);
        }

        // Auto-size all columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // Freeze the header rows (rows 0-3)
        sheet.createFreezePane(0, 4);

        FileOutputStream fos = new FileOutputStream(outputPath);
        workbook.write(fos);
        fos.close();
        workbook.close();

        System.out.println("Excel report created: " + outputPath);
    }

    // =========================================================================
    // UTILITY
    // =========================================================================
    private long percentile(List<Long> sortedTimes, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sortedTimes.size());
        index = Math.min(index, sortedTimes.size()) - 1;
        return sortedTimes.get(index);
    }
}
