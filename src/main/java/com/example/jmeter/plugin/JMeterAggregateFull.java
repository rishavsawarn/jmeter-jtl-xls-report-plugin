package com.example.jmeter.plugin;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.prefs.Preferences;

public class JMeterAggregateFull {

    // =========================================================================
    // INNER CLASS — holds actual test start/end time read from JTL timestamps
    // =========================================================================
    private static class TestTiming {
        long minTimestamp = Long.MAX_VALUE;  // earliest sample timestamp (epoch ms)

        void observe(long ts) {
            if (ts < minTimestamp) minTimestamp = ts;
        }

        boolean isValid() {
            return minTimestamp != Long.MAX_VALUE;
        }

        /** Returns e.g. "2026-04-03 10:15:00" */
        String format() {
            if (!isValid()) return "N/A";
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(minTimestamp));
        }
    }

    // =========================================================================
    // PREFERENCES — for remembering last used paths (Enhancement 6)
    // =========================================================================
    private static final Preferences PREFS =
            Preferences.userNodeForPackage(JMeterAggregateFull.class);

    public static final String PREF_JTL_PATH      = "lastJtlPath";
    public static final String PREF_BASELINE_PATH = "lastBaselinePath";
    public static final String PREF_OUTPUT_PATH   = "lastOutputPath";

    public static String getLastJtlPath()      { return PREFS.get(PREF_JTL_PATH,      ""); }
    public static String getLastBaselinePath() { return PREFS.get(PREF_BASELINE_PATH, ""); }
    public static String getLastOutputPath()   { return PREFS.get(PREF_OUTPUT_PATH,   ""); }

    public static void saveLastJtlPath(String path)      { PREFS.put(PREF_JTL_PATH,      path); }
    public static void saveLastBaselinePath(String path) { PREFS.put(PREF_BASELINE_PATH, path); }
    public static void saveLastOutputPath(String path)   { PREFS.put(PREF_OUTPUT_PATH,   path); }

    // =========================================================================
    // CLI ENTRY POINT
    // =========================================================================
    public static void main(String[] args) {
        try {
            Map<String, String> params = new HashMap<>();
            for (int i = 0; i < args.length - 1; i += 2) {
                params.put(args[i], args[i + 1]);
            }

            String jtlPath           = params.getOrDefault("--jtl-dir",      ".");
            String outputDirectory   = params.getOrDefault("--output-dir",   ".");
            String environment       = params.getOrDefault("--env",          "CI");
            String url               = params.getOrDefault("--app",          "N/A");
            String outputFileName    = params.getOrDefault("--output-name",  "JMeterReport");
            String transactionPrefix = params.getOrDefault("--prefix",       "");
            String scriptingName     = params.getOrDefault("--scripted-by",  "CI");
            double slaSeconds        = Double.parseDouble(
                                           params.getOrDefault("--sla-seconds", "10"));

            System.out.println("=========================================");
            System.out.println("  JTL to Excel Report — Headless Mode   ");
            System.out.println("=========================================");
            System.out.println("JTL Path         : " + jtlPath);
            System.out.println("Output Directory : " + outputDirectory);
            System.out.println("Output File Name : " + outputFileName + ".xlsx");
            System.out.println("Environment      : " + environment);
            System.out.println("Application      : " + url);
            System.out.println("Prefix Filter    : " + (transactionPrefix.isEmpty() ? "(none)" : transactionPrefix));
            System.out.println("Scripted By      : " + scriptingName);
            System.out.println("SLA (seconds)    : " + slaSeconds);
            System.out.println("=========================================");

            new JMeterAggregateFull().generateReport(
                    jtlPath, outputDirectory, environment, url,
                    outputFileName, transactionPrefix, scriptingName, slaSeconds
            );

            System.out.println("Done. ✅");

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // =========================================================================
    // CORE REPORT — CLI path (single sheet, no baseline)
    // =========================================================================
    public void generateReport(
            String jtlPath,
            String outputDirectory,
            String environment,
            String url,
            String outputFileName,
            String transactionPrefix,
            String scriptingName,
            double slaSeconds
    ) throws Exception {

        Map<String, List<Long>> responseTimes = new HashMap<>();
        Map<String, Integer>    errorCount    = new HashMap<>();
        TestTiming              timing        = new TestTiming();

        loadJtl(jtlPath, responseTimes, errorCount, transactionPrefix, timing);

        new File(outputDirectory).mkdirs();
        String outputPath = outputDirectory + File.separator + outputFileName + ".xlsx";

        if (new File(outputPath).exists()) {
            System.out.println("WARNING: Report already exists and will be overwritten: " + outputPath);
        }

        Workbook  workbook = new XSSFWorkbook();
        StylePack sp       = new StylePack(workbook);

        writeResultSheet(workbook, sp, "Current Run",
                responseTimes, errorCount, environment, url, scriptingName, slaSeconds, timing);

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            workbook.write(fos);
        }
        workbook.close();

        System.out.println("Excel report created: " + outputPath);
    }

    // =========================================================================
    // GUI REPORT — with optional baseline (1 or 3 sheets)
    // =========================================================================
    public void generateReport(
            String jtlPath,
            String baselineJtlPath,
            String outputDirectory,
            String environment,
            String url,
            String outputFileName,
            String transactionPrefixes,
            String scriptingName,
            double slaSeconds
    ) throws Exception {

        // Enhancement 6 — save last used paths
        saveLastJtlPath(jtlPath);
        if (baselineJtlPath != null && !baselineJtlPath.trim().isEmpty()) {
            saveLastBaselinePath(baselineJtlPath);
        }
        saveLastOutputPath(outputDirectory);

        Map<String, List<Long>> currentTimes  = new HashMap<>();
        Map<String, Integer>    currentErrors = new HashMap<>();
        TestTiming              currentTiming = new TestTiming();

        loadJtl(jtlPath, currentTimes, currentErrors, transactionPrefixes, currentTiming);

        new File(outputDirectory).mkdirs();
        String outputPath = outputDirectory + File.separator + outputFileName + ".xlsx";

        Workbook  workbook = new XSSFWorkbook();
        StylePack sp       = new StylePack(workbook);

        // Sheet 1 — Current Run (timing from actual JTL timestamps)
        writeResultSheet(workbook, sp, "Current Run",
                currentTimes, currentErrors, environment, url, scriptingName, slaSeconds, currentTiming);

        boolean hasBaseline = baselineJtlPath != null && !baselineJtlPath.trim().isEmpty();

        if (hasBaseline) {
            Map<String, List<Long>> baselineTimes  = new HashMap<>();
            Map<String, Integer>    baselineErrors = new HashMap<>();
            TestTiming              baselineTiming = new TestTiming();

            loadJtl(baselineJtlPath, baselineTimes, baselineErrors, transactionPrefixes, baselineTiming);

            // Sheet 2 — Baseline (timing from baseline JTL timestamps)
            writeResultSheet(workbook, sp, "Baseline",
                    baselineTimes, baselineErrors, environment, url, scriptingName, slaSeconds, baselineTiming);

            // Sheet 3 — Comparison
            writeComparisonSheet(workbook, sp,
                    currentTimes, currentErrors,
                    baselineTimes, baselineErrors);
        }

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            workbook.write(fos);
        }
        workbook.close();

        System.out.println("Excel report created: " + outputPath
                + (hasBaseline ? " (3 sheets — Current, Baseline, Comparison)" : ""));
    }

    // =========================================================================
    // Enhancement 8 — check if output file already exists (called from GUI)
    // =========================================================================
    public boolean outputFileExists(String outputDirectory, String outputFileName) {
        return new File(outputDirectory + File.separator + outputFileName + ".xlsx").exists();
    }

    // =========================================================================
    // SMART JTL LOADER — file or directory, also captures TestTiming
    // =========================================================================
    private void loadJtl(
            String path,
            Map<String, List<Long>> responseTimes,
            Map<String, Integer> errorCount,
            String prefixes,
            TestTiming timing         // collects min/max timestamps from JTL
    ) throws Exception {

        File f = new File(path);
        if (!f.exists()) throw new RuntimeException("Path not found: " + path);

        List<File> jtlFiles = new ArrayList<>();

        if (f.isFile()) {
            if (!f.getName().toLowerCase().endsWith(".jtl"))
                throw new RuntimeException("File is not a .jtl file: " + path);
            jtlFiles.add(f);
            System.out.println("Single JTL file: " + f.getName());
        } else {
            File[] found = f.listFiles((d, name) -> name.toLowerCase().endsWith(".jtl"));
            if (found == null || found.length == 0)
                throw new RuntimeException("No .jtl files found in directory: " + path);
            jtlFiles.addAll(Arrays.asList(found));
            System.out.println("Found " + jtlFiles.size() + " JTL file(s) in: " + path);
        }

        for (File jtl : jtlFiles) {
            System.out.println("Parsing: " + jtl.getName());
            parseJTL(jtl, responseTimes, errorCount, prefixes, timing);
        }

        if (responseTimes.isEmpty()) {
            String prefixMsg = (prefixes == null || prefixes.trim().isEmpty())
                ? "no prefix filter applied"
                : "prefix filter: '" + prefixes + "'";
            throw new RuntimeException(
                "No sample data found in: " + path + " (" + prefixMsg + ").\n" +
                "Possible causes:\n" +
                "  • JTL file has only headers and no data rows\n" +
                "  • Transaction prefix does not match any labels in the JTL\n" +
                "  • JTL file is corrupt or in an unexpected format"
            );
        }
    }

    // =========================================================================
    // JTL PARSING — reads timestamps from col 0 into TestTiming
    // =========================================================================
    private void parseJTL(
            File jtlFile,
            Map<String, List<Long>> responseTimes,
            Map<String, Integer> errorCount,
            String prefixes,
            TestTiming timing
    ) throws Exception {

        List<String> prefixList = new ArrayList<>();
        if (prefixes != null && !prefixes.trim().isEmpty()) {
            for (String p : prefixes.split(",")) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) prefixList.add(trimmed);
            }
        }

        try (BufferedReader br = new BufferedReader(new FileReader(jtlFile))) {
            String  line;
            boolean firstLine = true;
            int     dataRows  = 0;

            while ((line = br.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }

                String[] cols = line.split(",");
                if (cols.length < 8) continue;

                dataRows++;

                String label = cols[2].trim();

                if (!prefixList.isEmpty()) {
                    boolean matched = false;
                    for (String prefix : prefixList) {
                        if (label.startsWith(prefix)) { matched = true; break; }
                    }
                    if (!matched) continue;
                }

                long    elapsed;
                boolean success;
                long    timestamp;

                try {
                    timestamp = Long.parseLong(cols[0].trim()); // col 0 = timeStamp (epoch ms)
                    elapsed   = Long.parseLong(cols[1].trim()); // col 1 = elapsed ms
                    success   = Boolean.parseBoolean(cols[7].trim());
                } catch (NumberFormatException e) {
                    continue; // skip corrupt rows
                }

                // Feed timestamp into timing tracker
                timing.observe(timestamp);

                responseTimes.putIfAbsent(label, new ArrayList<>());
                responseTimes.get(label).add(elapsed);

                if (!success) {
                    errorCount.put(label, errorCount.getOrDefault(label, 0) + 1);
                }
            }

            if (dataRows == 0) {
                throw new RuntimeException(
                    "JTL file contains no sample data (header row only): " + jtlFile.getName()
                );
            }
        }
    }

    // =========================================================================
    // WRITE A RESULT SHEET
    // Info row now shows actual test start/end time from JTL (not report gen time)
    // =========================================================================
    private void writeResultSheet(
            Workbook workbook,
            StylePack sp,
            String sheetName,
            Map<String, List<Long>> responseTimes,
            Map<String, Integer> errors,
            String env,
            String url,
            String scriptingName,
            double slaSeconds,
            TestTiming timing
    ) {
        Sheet sheet = workbook.createSheet(sheetName);
        int rowNum = 0;

        // Title
        Row  title     = sheet.createRow(rowNum++);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("JMeter Performance Report — " + sheetName);
        titleCell.setCellStyle(sp.titleStyle);

        // Info row — test start/end derived from JTL timestamps
        Row info = sheet.createRow(rowNum++);
        info.createCell(0).setCellValue(
                "Environment: " + env +
                " | URL: " + url +
                " | Scripted By: " + scriptingName +
                " | SLA: " + slaSeconds + "s (P90)" +
                " | Test Start: " + timing.format()
        );

        rowNum++; // blank row

        // Headers
        String[] headers = { "Transaction", "Samples", "Average(ms)",
                              "P90(ms)", "P95(ms)", "Min(ms)", "Max(ms)", "Error %" };
        Row headerRow = sheet.createRow(rowNum++);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(sp.headerStyle);
        }

        // Data rows
        List<String> transactions = sortedStrings(responseTimes.keySet());
        for (String txn : transactions) {
            List<Long> times   = sortedLongs(responseTimes.get(txn));
            int        samples = times.size();
            int        error   = errors.getOrDefault(txn, 0);
            double     avg     = average(times);
            long       p90     = percentile(times, 90);
            long       p95     = percentile(times, 95);
            long       min     = times.get(0);
            long       max     = times.get(times.size() - 1);
            double     errPct  = (double) error / samples * 100;

            Row row = sheet.createRow(rowNum++);

            setCellStr(row, 0, txn,                                      sp.centerStyle);
            setCellInt(row, 1, samples,                                   sp.centerStyle);
            setCellLng(row, 2, Math.round(avg),                           sp.centerStyle);
            setCellLng(row, 3, p90, p90 / 1000.0 > slaSeconds ? sp.redStyle : sp.centerStyle);
            setCellLng(row, 4, p95,                                       sp.centerStyle);
            setCellLng(row, 5, min,                                       sp.centerStyle);
            setCellLng(row, 6, max,                                       sp.centerStyle);
            setCellStr(row, 7, String.format("%.2f%%", errPct),           sp.centerStyle);
        }

        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
        sheet.createFreezePane(0, 4);
    }

    // =========================================================================
    // WRITE COMPARISON SHEET (Sheet 3)
    // =========================================================================
    private void writeComparisonSheet(
            Workbook workbook,
            StylePack sp,
            Map<String, List<Long>> currentTimes,
            Map<String, Integer>    currentErrors,
            Map<String, List<Long>> baselineTimes,
            Map<String, Integer>    baselineErrors
    ) {
        Sheet sheet = workbook.createSheet("Comparison");
        int rowNum = 0;

        Row  title     = sheet.createRow(rowNum++);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("Comparison — Current Run vs Baseline");
        titleCell.setCellStyle(sp.titleStyle);

        Row legend = sheet.createRow(rowNum++);
        legend.createCell(0).setCellValue(
            "Green = Improved or equal  |  Yellow = Slightly Degraded (< 10%)  |  Red = Degraded (>= 10%)  |  Blue = New Transaction  |  Threshold: 10% (P90)"
        );

        rowNum++; // blank row

        // Group header row
        Row groupRow = sheet.createRow(rowNum++);
        groupRow.createCell(0).setCellStyle(sp.groupHeaderStyle);

        Cell bLabel = groupRow.createCell(1);
        bLabel.setCellValue("Baseline");
        bLabel.setCellStyle(sp.groupHeaderStyle);
        for (int i = 2; i <= 7; i++) groupRow.createCell(i).setCellStyle(sp.groupHeaderStyle);

        Cell cLabel = groupRow.createCell(8);
        cLabel.setCellValue("Current Run");
        cLabel.setCellStyle(sp.groupHeaderStyle);
        for (int i = 9; i <= 14; i++) groupRow.createCell(i).setCellStyle(sp.groupHeaderStyle);

        Cell dLabel = groupRow.createCell(15);
        dLabel.setCellValue("Delta (Current vs Baseline)");
        dLabel.setCellStyle(sp.deltaHeaderStyle);
        for (int i = 16; i <= 20; i++) groupRow.createCell(i).setCellStyle(sp.deltaHeaderStyle);

        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 1,  7));
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 8,  14));
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 15, 20));

        // Column header row
        String[] colHeaders = {
            "Transaction",
            "Samples", "Average(ms)", "P90(ms)", "P95(ms)", "Min(ms)", "Max(ms)", "Error %",
            "Samples", "Average(ms)", "P90(ms)", "P95(ms)", "Min(ms)", "Max(ms)", "Error %",
            "Samples Diff", "Avg Diff(ms)", "P90 Diff(ms)", "P90 Diff%", "Error% Diff", "Status"
        };

        Row colHeaderRow = sheet.createRow(rowNum++);
        for (int i = 0; i < colHeaders.length; i++) {
            Cell c = colHeaderRow.createCell(i);
            c.setCellValue(colHeaders[i]);
            c.setCellStyle(i >= 15 ? sp.deltaHeaderStyle : sp.headerStyle);
        }

        // Data rows
        Set<String> allTxns = new LinkedHashSet<>();
        allTxns.addAll(sortedStrings(baselineTimes.keySet()));
        allTxns.addAll(sortedStrings(currentTimes.keySet()));

        for (String txn : allTxns) {

            Row row = sheet.createRow(rowNum++);
            setCellStr(row, 0, txn, sp.centerStyle);

            // Baseline metrics
            boolean inBaseline = baselineTimes.containsKey(txn);
            int    bSamples = 0; double bAvg = 0; long bP90 = 0;
            long   bP95 = 0; long bMin = 0; long bMax = 0; double bErrPct = 0;

            if (inBaseline) {
                List<Long> bt = sortedLongs(baselineTimes.get(txn));
                bSamples = bt.size();
                int bErr = baselineErrors.getOrDefault(txn, 0);
                bAvg = average(bt); bP90 = percentile(bt, 90); bP95 = percentile(bt, 95);
                bMin = bt.get(0);   bMax = bt.get(bt.size() - 1);
                bErrPct = (double) bErr / bSamples * 100;

                setCellInt(row, 1, bSamples,                         sp.centerStyle);
                setCellLng(row, 2, Math.round(bAvg),                 sp.centerStyle);
                setCellLng(row, 3, bP90,                             sp.centerStyle);
                setCellLng(row, 4, bP95,                             sp.centerStyle);
                setCellLng(row, 5, bMin,                             sp.centerStyle);
                setCellLng(row, 6, bMax,                             sp.centerStyle);
                setCellStr(row, 7, String.format("%.2f%%", bErrPct), sp.centerStyle);
            } else {
                for (int i = 1; i <= 7; i++) setCellStr(row, i, "N/A", sp.naStyle);
            }

            // Current metrics
            boolean inCurrent = currentTimes.containsKey(txn);
            int    cSamples = 0; double cAvg = 0; long cP90 = 0;
            long   cP95 = 0; long cMin = 0; long cMax = 0; double cErrPct = 0;

            if (inCurrent) {
                List<Long> ct = sortedLongs(currentTimes.get(txn));
                cSamples = ct.size();
                int cErr = currentErrors.getOrDefault(txn, 0);
                cAvg = average(ct); cP90 = percentile(ct, 90); cP95 = percentile(ct, 95);
                cMin = ct.get(0);   cMax = ct.get(ct.size() - 1);
                cErrPct = (double) cErr / cSamples * 100;

                setCellInt(row, 8,  cSamples,                         sp.centerStyle);
                setCellLng(row, 9,  Math.round(cAvg),                 sp.centerStyle);
                setCellLng(row, 10, cP90,                             sp.centerStyle);
                setCellLng(row, 11, cP95,                             sp.centerStyle);
                setCellLng(row, 12, cMin,                             sp.centerStyle);
                setCellLng(row, 13, cMax,                             sp.centerStyle);
                setCellStr(row, 14, String.format("%.2f%%", cErrPct), sp.centerStyle);
            } else {
                for (int i = 8; i <= 14; i++) setCellStr(row, i, "N/A", sp.naStyle);
            }

            // Delta
            if (inBaseline && inCurrent) {
                int    samplesDiff = cSamples - bSamples;
                long   avgDiffMs   = Math.round(cAvg - bAvg);
                long   p90DiffMs   = cP90 - bP90;
                double p90DiffPct  = bP90 == 0 ? 0 : ((double) p90DiffMs / bP90) * 100;
                double errDiff     = cErrPct - bErrPct;

                CellStyle diffStyle;
                String    status;

                if (p90DiffPct <= 0)      { diffStyle = sp.greenStyle;  status = "Improved"; }
                else if (p90DiffPct < 10) { diffStyle = sp.yellowStyle; status = "Slightly Degraded"; }
                else                      { diffStyle = sp.redStyle;    status = "Degraded"; }

                setCellInt(row, 15, samplesDiff,                         diffStyle);
                setCellLng(row, 16, avgDiffMs,                           diffStyle);
                setCellLng(row, 17, p90DiffMs,                           diffStyle);
                setCellStr(row, 18, String.format("%.2f%%", p90DiffPct), diffStyle);
                setCellStr(row, 19, String.format("%.2f%%", errDiff),    diffStyle);
                setCellStr(row, 20, status,                              diffStyle);

            } else if (!inBaseline) {
                for (int i = 15; i <= 19; i++) setCellStr(row, i, "New", sp.blueStyle);
                setCellStr(row, 20, "New Txn", sp.blueStyle);
            } else {
                for (int i = 15; i <= 19; i++) setCellStr(row, i, "Missing", sp.naStyle);
                setCellStr(row, 20, "Not in Current", sp.naStyle);
            }
        }

        for (int i = 0; i < colHeaders.length; i++) sheet.autoSizeColumn(i);
        sheet.createFreezePane(0, 5);
    }

    // =========================================================================
    // STYLE PACK
    // =========================================================================
    private static class StylePack {

        final CellStyle titleStyle;
        final CellStyle headerStyle;
        final CellStyle groupHeaderStyle;
        final CellStyle deltaHeaderStyle;
        final CellStyle centerStyle;
        final CellStyle redStyle;
        final CellStyle greenStyle;
        final CellStyle yellowStyle;
        final CellStyle blueStyle;
        final CellStyle naStyle;

        StylePack(Workbook wb) {

            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            Font boldWhite = wb.createFont();
            boldWhite.setBold(true);
            boldWhite.setColor(IndexedColors.WHITE.getIndex());

            titleStyle = wb.createCellStyle();
            titleStyle.setFont(boldFont);
            titleStyle.setAlignment(HorizontalAlignment.LEFT);

            headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyBorder(headerStyle);

            groupHeaderStyle = wb.createCellStyle();
            groupHeaderStyle.setFont(boldWhite);
            groupHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
            groupHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            groupHeaderStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            groupHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyBorder(groupHeaderStyle);

            deltaHeaderStyle = wb.createCellStyle();
            deltaHeaderStyle.setFont(boldWhite);
            deltaHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
            deltaHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            deltaHeaderStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            deltaHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyBorder(deltaHeaderStyle);

            centerStyle = wb.createCellStyle();
            centerStyle.setAlignment(HorizontalAlignment.CENTER);
            centerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(centerStyle);

            redStyle = wb.createCellStyle();
            redStyle.cloneStyleFrom(centerStyle);
            redStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            redStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            greenStyle = wb.createCellStyle();
            greenStyle.cloneStyleFrom(centerStyle);
            greenStyle.setFillForegroundColor(IndexedColors.BRIGHT_GREEN.getIndex());
            greenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            yellowStyle = wb.createCellStyle();
            yellowStyle.cloneStyleFrom(centerStyle);
            yellowStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            yellowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            blueStyle = wb.createCellStyle();
            blueStyle.cloneStyleFrom(centerStyle);
            blueStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            blueStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            naStyle = wb.createCellStyle();
            naStyle.cloneStyleFrom(centerStyle);
            naStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            naStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        private void applyBorder(CellStyle s) {
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
        }
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================
    private long percentile(List<Long> sorted, int pct) {
        int index = (int) Math.ceil(pct / 100.0 * sorted.size());
        return sorted.get(Math.min(index, sorted.size()) - 1);
    }

    private double average(List<Long> times) {
        return times.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private List<Long> sortedLongs(Collection<Long> times) {
        List<Long> list = new ArrayList<>(times);
        Collections.sort(list);
        return list;
    }

    private List<String> sortedStrings(Collection<String> keys) {
        List<String> list = new ArrayList<>(keys);
        Collections.sort(list);
        return list;
    }

    private void setCellLng(Row row, int col, long value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private void setCellInt(Row row, int col, int value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private void setCellStr(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }
}
