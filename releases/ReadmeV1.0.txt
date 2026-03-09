JMeter JTL to Excel Smart Report Plugin – v1.0.0

This release introduces the first version of the JMeter JTL to Excel Smart Report Plugin, a custom Apache JMeter listener designed to generate structured Excel performance reports from JTL result files.

The plugin simplifies performance analysis by converting raw JMeter results into a clean, transaction-level Excel report with key performance metrics and automatic SLA validation.

Overview

Performance engineers often need to manually process JTL files to generate stakeholder-friendly reports. This plugin automates that process by generating a formatted Excel report containing transaction-wise metrics and highlighting SLA breaches.

Key Features

• Generate Excel (.xlsx) reports directly from JMeter JTL files  
• Transaction-wise performance metrics  
• Automatic SLA validation based on P90 response time  
• Visual highlighting of SLA breaches in the Excel report  
• Clean and formatted Excel output for reporting  
• Progress bar during report generation  
• Built-in logging console with clear options  

Metrics Included

The generated report includes the following metrics for each transaction:

- Samples
- Average Response Time
- P90 Response Time
- Minimum Response Time
- Maximum Response Time
- Error Percentage

If the P90 response time exceeds the defined SLA threshold, the value is automatically highlighted in red to make performance issues easy to identify.

Installation

1. Download the plugin JAR file from this release.
2. Copy the JAR file into the following directory:

   JMETER_HOME/lib/ext

3. Restart Apache JMeter.

Usage

1. Open Apache JMeter.
2. Add Listener → JTL XLS Smart Report Generator.
3. Provide the required inputs:
   - JTL file path
   - Environment name
   - Application or URL
   - Transaction label prefix
   - SLA threshold
4. Click Generate Report.

The plugin will automatically generate the Excel performance report.

Notes

This is the initial release of the plugin. Feedback, feature requests, and contributions are welcome to improve the tool for the performance engineering community.

Author

Rishav
Performance Engineer
