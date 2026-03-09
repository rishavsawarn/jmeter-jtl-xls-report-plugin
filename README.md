# JMeter JTL to Excel Smart Report Plugin

A custom Apache JMeter plugin that generates Excel SLA reports from JTL result files.

The plugin helps performance engineers/Testers quickly create clean, formatted transaction-level performance reports with SLA validation.

---

## Features

- Generate Excel (.xlsx) report from JMeter JTL files
- Transaction-wise performance metrics
- SLA validation support
- P90 highlighting when SLA is breached
- Center-aligned formatted Excel report
- Progress bar during report generation
- Log console with clear / clear all support

---

## Metrics Generated

The plugin calculates the following metrics per transaction:

| Metric | Description |
|------|-------------|
| Samples | Total number of requests |
| Average Response Time | Mean response time |
| P90 Response Time | 90th percentile response time |
| Minimum Response Time | Fastest response time |
| Maximum Response Time | Slowest response time |
| Error % | Percentage of failed requests |

---

## SLA Validation

Users can define an SLA threshold for P90 response time.

If:

P90 > SLA → highlighted in red in the Excel report.

---

## Installation

### 1. Build the plugin

```bash
mvn clean package
```

### 2. Copy the generated JAR

Copy the generated JAR file to:

```
JMETER_HOME/lib/ext
```

### 3. Restart JMeter

Restart JMeter to load the plugin.

---

## Usage

1. Open Apache JMeter  
2. Add Listener → **JTL XLS Smart Report Generator**  
3. Provide the following inputs:

| Input | Description |
|------|-------------|
| JTL file path | Path to JMeter JTL result file |
| Environment | Test environment name |
| URL / Application name | Application under test |
| Transaction label prefix | Prefix used in JMeter transaction labels |
| SLA threshold | P90 SLA value |

4. Click **Generate Report**

The Excel report will be generated automatically.

---

## Example Output

| Transaction | Samples | Avg(ms) | P90(ms) | Min(ms) | Max(ms) | Error % |
|------------|--------|--------|--------|--------|--------|--------|
| Login | 100 | 120 | 240 | 80 | 300 | 0.5 |

If the SLA is breached, the P90 value is highlighted in red.

---

## Project Structure

```
src
 └── main
     ├── java
     │   └── com/example/jmeter/plugin
     │       ├── JtlToXlsGui.java
     │       ├── JtlToXlsListener.java
     │       └── JMeterAggregateFull.java
     │
     └── resources
         ├── META-INF/services
         └── jmeter-plugin.properties
```

---

## Author

Rishav

---

## Contributions

Pull requests and improvements are welcome.
