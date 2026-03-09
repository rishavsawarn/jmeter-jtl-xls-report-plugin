package com.example.jmeter.plugin;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

public class JMeterAggregateFull {

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
            throw new RuntimeException("No JTL files found.");
        }

        Map<String, List<Long>> responseTimes = new HashMap<>();
        Map<String, Integer> errorCount = new HashMap<>();

        for (File jtl : jtlFiles) {
            parseJTL(jtl, responseTimes, errorCount, transactionPrefix);
        }

        String outputPath = outputDirectory + "/" + outputFileName + ".xlsx";

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
                continue;
            }

            String[] cols = line.split(",");

            if (cols.length < 8) continue;

            String label = cols[2].trim();

            if (!label.startsWith(prefix)) continue;

            long elapsed = Long.parseLong(cols[1]);
            boolean success = Boolean.parseBoolean(cols[7]);

            responseTimes.putIfAbsent(label, new ArrayList<>());
            responseTimes.get(label).add(elapsed);

            if (!success) {
                errorCount.put(label, errorCount.getOrDefault(label, 0) + 1);
            }
        }

        br.close();
    }

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

        /* SLA FAIL STYLE */

        CellStyle redStyle = workbook.createCellStyle();
        redStyle.cloneStyleFrom(centerStyle);
        redStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
        redStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        int rowNum = 0;

        Row title = sheet.createRow(rowNum++);
        title.createCell(0).setCellValue("JMeter Performance Report");

        Row info = sheet.createRow(rowNum++);
        info.createCell(0).setCellValue(
                "Environment: " + env +
                        " | URL: " + url +
                        " | Scripted By: " + scriptingName
        );

        rowNum++;

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

        List<String> transactions = new ArrayList<>(responseTimes.keySet());
        Collections.sort(transactions);

        for (String txn : transactions) {

            List<Long> times = responseTimes.get(txn);
            Collections.sort(times);

            int samples = times.size();
            int error = errors.getOrDefault(txn, 0);

            double avg = times.stream().mapToLong(Long::longValue).average().orElse(0);
            long min = times.get(0);
            long max = times.get(times.size() - 1);

            long p90 = percentile(times, 90);
            long p95 = percentile(times, 95);

            double errorPercent = ((double) error / samples) * 100;

            Row row = sheet.createRow(rowNum++);

            Cell c0 = row.createCell(0);
            c0.setCellValue(txn);
            c0.setCellStyle(centerStyle);

            Cell c1 = row.createCell(1);
            c1.setCellValue(samples);
            c1.setCellStyle(centerStyle);

            Cell c2 = row.createCell(2);
            c2.setCellValue(avg);
            c2.setCellStyle(centerStyle);

            Cell c3 = row.createCell(3);
            c3.setCellValue(p90);

            if (p90 / 1000.0 > slaSeconds) {
                c3.setCellStyle(redStyle);
            } else {
                c3.setCellStyle(centerStyle);
            }

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
            c7.setCellValue(errorPercent);
            c7.setCellStyle(centerStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        sheet.createFreezePane(0, 4);

        FileOutputStream fos = new FileOutputStream(outputPath);

        workbook.write(fos);

        fos.close();
        workbook.close();

        System.out.println("Excel report created: " + outputPath);
    }

    private long percentile(List<Long> sortedTimes, int percentile) {

        int index = (int) Math.ceil(percentile / 100.0 * sortedTimes.size());
        index = Math.min(index, sortedTimes.size()) - 1;

        return sortedTimes.get(index);
    }
}