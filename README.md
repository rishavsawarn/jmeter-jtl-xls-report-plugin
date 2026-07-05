# JMeter JTL to Excel Smart Report Plugin

A custom Apache JMeter plugin that generates Excel SLA reports from JTL result files.  
The plugin helps performance engineers/Testers quickly create clean, formatted transaction-level performance reports with SLA validation.

---

## Features

- Generate Excel (.xlsx) report from JMeter JTL files
- Transaction-wise performance metrics
- SLA validation support
- P90 highlighting when SLA is breached
- **Baseline comparison** — compare a run against a baseline JTL with color-coded regression indicators
- **Error Analysis** — per-transaction breakdown of failures by type (Timeout, 4xx, 5xx, Assertion, Other)
- Center-aligned formatted Excel report
- Progress bar during report generation
- Log console with clear / clear all support
- **Headless CLI mode** — run without JMeter GUI for CI/CD pipelines

---

## Metrics Generated

The plugin calculates the following metrics per transaction:

| Metric | Description |
|------|-------------|
| Samples | Total number of requests |
| Average Response Time | Mean response time |
| P90 Response Time | 90th percentile response time |
| P95 Response Time | 95th percentile response time |
| Minimum Response Time | Fastest response time |
| Maximum Response Time | Slowest response time |
| Error % | Percentage of failed requests |

---

## Report Sheets

Depending on the inputs provided, the generated workbook can contain:

| Sheet | When | Contents |
|------|------|----------|
| Current Run | Always | Transaction-wise metrics for the run |
| Baseline | When a baseline JTL is provided | Same metrics for the baseline run |
| Comparison | When a baseline JTL is provided | Current vs Baseline deltas with regression indicators |
| Error Analysis | Always | Per-transaction failure breakdown by type |

---

## SLA Validation

Users can define an SLA threshold for P90 response time.

If:

P90 > SLA → highlighted in red in the Excel report.

---

## Error Analysis (v3.1.0)

The **Error Analysis** sheet breaks every failed sample down by type, so a single
"Error %" no longer hides *why* requests failed. Failures are classified using the
JTL's `responseCode`, `responseMessage`, and `failureMessage` fields:

| Category | Meaning |
|------|-------------|
| Timeout | Socket / read timeouts (e.g. `SocketTimeoutException`, or a "timeout" message) |
| 4xx (Client) | HTTP status 400–499 |
| 5xx (Server) | HTTP status 500–599 |
| Assertion | HTTP request succeeded (2xx/3xx) but a JMeter assertion failed |
| Other | Connection / non-HTTP errors (e.g. `ConnectException`, response code `0`) |

The sheet lists only transactions that recorded at least one error, adds a **TOTAL**
row, and highlights non-zero counts (red for network/server errors, yellow for
assertion failures). This is especially useful for surfacing assertion failures,
which look like passing HTTP calls but indicate a bad response body.

---

## Installation

You can install the plugin directly from the **JMeter Plugins Manager** — no manual JAR copying needed.

1. Open JMeter
2. Go to **Options → Plugins Manager**
3. Search for **JTL XLS Smart Report Generator**
4. Click **Apply Changes and Restart JMeter**

Alternatively, download the latest `.jar` from the
[Releases](https://github.com/rishavsawarn/jmeter-jtl-xls-report-plugin/releases)
page and place it in `<JMETER_HOME>/lib/ext`, then restart JMeter.

---

## Usage

### Option A — JMeter GUI

1. Open Apache JMeter
2. Add Listener → **JTL XLS Smart Report Generator**
3. Provide the following inputs:

| Input | Description |
|------|-------------|
| JTL file path | Path to JMeter JTL result file |
| Baseline JTL path | (Optional) Baseline JTL to compare against |
| Environment | Test environment name |
| URL / Application name | Application under test |
| Transaction label prefix | Prefix used in JMeter transaction labels |
| SLA threshold | P90 SLA value in seconds |

4. Click **Generate Report**

The Excel report will be generated automatically.

------------------------------------------------------------------

### Option B — Headless CLI (CI/CD / GitHub Actions)

The plugin can run without JMeter GUI, directly from the command line or inside a CI/CD pipeline.

```bash
java -jar jmeter-jtl-xls-report-plugin-v3.1.0.jar \
  --jtl-dir      <path-to-folder-containing-jtl-files> \
  --output-dir   <folder-where-xlsx-will-be-saved> \
  --output-name  MyReport \
  --env          PERF \
  --app          MyApplication \
  --prefix       PC_ \
  --scripted-by  YourName \
  --sla-seconds  10
```

#### Arguments

| Argument | Description | Default |
|---|---|---|
| `--jtl-dir` | Folder containing `.jtl` files | `.` |
| `--output-dir` | Folder where the `.xlsx` will be saved | `.` |
| `--output-name` | Output filename without extension | `JMeterReport` |
| `--env` | Environment label shown in report header | `CI` |
| `--app` | Application / URL label shown in report header | `N/A` |
| `--prefix` | Transaction label prefix filter (e.g. `PC_`) | *(all transactions)* |
| `--scripted-by` | Tester name shown in report header | `CI` |
| `--sla-seconds` | P90 SLA threshold in **seconds** | `10` |

**Note:** `--sla-seconds` accepts seconds, not milliseconds.  


## Example Output

**Current Run sheet**

| Transaction | Samples | Avg(ms) | P90(ms) | P95(ms) | Min(ms) | Max(ms) | Error % |
|------------|--------|--------|--------|--------|--------|--------|--------|
| Login | 100 | 120 | 240 | 260 | 80 | 300 | 0.50% |

If the SLA is breached, the P90 value is highlighted in red.

**Error Analysis sheet**

| Transaction | Total Errors | Timeout | 4xx (Client) | 5xx (Server) | Assertion | Other | Error % |
|------------|--------|--------|--------|--------|--------|--------|--------|
| Checkout | 3 | 0 | 0 | 1 | 2 | 0 | 3.00% |
| **TOTAL** | **3** | **0** | **0** | **1** | **2** | **0** | **3.00%** |

---

## Project Structure

```
src
 └── main
     ├── java
     │   └── com/example/jmeter/plugin
     │       ├── JtlToXlsGui.java          ← GUI listener (JMeter UI)
     │       ├── JtlToXlsListener.java     ← Test element wiring
     │       └── JMeterAggregateFull.java  ← Core logic + CLI entry point
     │
     └── resources
         ├── META-INF/services
         └── jmeter-plugin.properties
```

## Changelog

- **v3.1.0** — Error Analysis sheet: per-transaction failure breakdown by type (Timeout / 4xx / 5xx / Assertion / Other) with a totals row and color highlighting; bundled commons-compress so .xlsx generation works reliably inside JMeter
- **v3.0.0** — Baseline JTL comparison (Current / Baseline / Comparison sheets); GUI Baseline Directory field; remembers last-used paths between sessions
- **v2.0.0** — Headless CLI mode; P95 metric column; auto-create output directory; empty prefix now includes all transactions
- **v1.0.0** — Initial release: Excel SLA reports with P90 highlighting

---

## Author

Rishav

---

## Contributions

Pull requests and improvements are welcome.
